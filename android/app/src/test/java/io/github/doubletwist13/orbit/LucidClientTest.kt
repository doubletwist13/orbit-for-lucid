package io.github.doubletwist13.orbit

import kotlinx.coroutines.test.runTest
import mobilegateway.protos.LoginSessionOuterClass.GetNewJWTTokenResponse
import mobilegateway.protos.LoginSessionOuterClass.GetUserVehiclesResponse
import mobilegateway.protos.LoginSessionOuterClass.LoginResponse
import mobilegateway.protos.LoginSessionOuterClass.SessionInfo
import mobilegateway.protos.VehicleStateServiceOuterClass.Vehicle
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer

class LucidClientTest {
    private fun fake(grpcStatus: Int = 0, message: ByteArray = ByteArray(0), seen: MutableList<String> = mutableListOf()) =
        LucidClient(
            deviceId = "dev", now = { 1000 },
            http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                seen += chain.request().url.encodedPath + " auth=" + chain.request().header("authorization")
                val body = ByteBuffer.allocate(5 + message.size).put(0).putInt(message.size).put(message).array()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_2).code(200).message("OK")
                    .header("grpc-status", grpcStatus.toString()).header("grpc-message", "token%20expired")
                    .body(body.toResponseBody("application/grpc".toMediaType())).build()
            }).build(),
        )

    /** Answers each call from [reply] (gRPC method name, 0-based count of calls to it) -> (grpc status, message). */
    private fun scripted(
        seen: MutableList<String>, reply: (String, Int) -> Pair<Int, ByteArray>,
    ) = LucidClient(
        deviceId = "dev", now = { 1000 },
        http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val method = chain.request().url.encodedPath.substringAfterLast('/')
            val (status, message) = reply(method, seen.count { it == method })
            seen += method
            val body = ByteBuffer.allocate(5 + message.size).put(0).putInt(message.size).put(message).array()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_2).code(200).message("OK")
                .header("grpc-status", status.toString()).body(body.toResponseBody("application/grpc".toMediaType()))
                .build()
        }).build(),
    )

    private fun session(expiry: Int) = SessionInfo.newBuilder().setIdToken("id").setExpiryTimeSec(expiry)
        .setRefreshToken("rt").build().let { GetNewJWTTokenResponse.newBuilder().setSessionInfo(it).build() }

    private val oneVehicle = GetUserVehiclesResponse.newBuilder()
        .addUserVehicleData(Vehicle.newBuilder().setVehicleId("v1")).build().toByteArray()

    @Test
    fun rateLimitedStatusRetriesThenSucceeds() = runTest {
        val seen = mutableListOf<String>()
        val status = scripted(seen) { method, n ->
            when {
                method == "GetNewJWTToken" -> 0 to session(2000).toByteArray()
                n == 0 -> 8 to ByteArray(0)
                else -> 0 to oneVehicle
            }
        }.status("rt")
        assertEquals("v1", status.vehicleId)
        assertEquals(listOf("GetNewJWTToken", "GetUserVehicles", "GetUserVehicles"), seen)
    }

    @Test
    fun secondStatusWithinTenSecondsUsesCache() = runTest {
        val seen = mutableListOf<String>()
        val client = scripted(seen) { method, _ ->
            0 to if (method == "GetNewJWTToken") session(2000).toByteArray() else oneVehicle
        }
        client.status("rt")
        assertTrue(client.status("rt").fromCache)
        assertEquals(listOf("GetNewJWTToken", "GetUserVehicles"), seen)
    }

    @Test
    fun tokenRefreshesOnlyWhenUnderSixtySecondsRemain() = runTest {
        for ((expiry, refreshes) in listOf(2000 to 1, 1030 to 2)) {
            val seen = mutableListOf<String>()
            val client = scripted(seen) { method, _ ->
                0 to if (method == "GetNewJWTToken") session(expiry).toByteArray() else ByteArray(0)
            }
            client.wake("rt", "v1")
            client.wake("rt", "v1")
            assertEquals("expiry $expiry", refreshes, seen.count { it == "GetNewJWTToken" })
        }
    }

    @Test
    fun loginReturnsRefreshTokenAndSendsNoAuthHeader() = runTest {
        val session = SessionInfo.newBuilder().setIdToken("id").setExpiryTimeSec(2000).setRefreshToken("rt").build()
        val seen = mutableListOf<String>()
        val rt = fake(message = LoginResponse.newBuilder().setSessionInfo(session).build().toByteArray(), seen = seen)
            .login("u", "p")
        assertEquals("rt", rt)
        assertEquals("/mobilegateway.protos.LoginSession/Login auth=null", seen.single())
    }

    @Test
    fun grpcErrorStatusIsDecodedAndFlaggedUnauthenticated() = runTest {
        try {
            fake(grpcStatus = 16).login("u", "p")
            fail("expected LucidException")
        } catch (e: LucidException) {
            assertTrue(e.unauthenticated)
            assertEquals("token expired [Login]", e.message)
        }
    }
}
