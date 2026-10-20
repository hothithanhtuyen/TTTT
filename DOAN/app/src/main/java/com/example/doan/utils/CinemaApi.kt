package com.example.doan.utils

import android.os.Handler
import android.os.Looper
import com.example.doan.BuildConfig
import com.example.doan.model.BookingDraft
import com.example.doan.model.CinemaOrder
import com.example.doan.model.CinemaTicket
import com.example.doan.model.PendingCounterOrder
import com.example.doan.model.TicketCheckResult
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

object CinemaApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl get() = BuildConfig.VNPAY_API_BASE_URL.trimEnd('/')

    fun createOrder(draft: BookingDraft, method: String, callback: (Result<CinemaOrder>) -> Unit) {
        val orderId = UUID.randomUUID().toString().replace("-", "").uppercase()
        val body = JSONObject()
            .put("orderId", orderId)
            .put("paymentMethod", method)
            .put("title", draft.movie.title)
            .put("bookingDate", draft.bookingDate)
            .put("cinema", draft.cinema)
            .put("showtime", draft.showtime)
            .put("seats", JSONArray(draft.seats))
            .put("province", draft.province)
            .put("customerName", draft.customerName)
            .put("posterUrl", draft.movie.posterUrl)
        execute("/api/orders/create", "POST", body, true) { result ->
            result.fold(
                onSuccess = { json -> callback(Result.success(json.toOrder(orderId))) },
                onFailure = { callback(Result.failure(it)) },
            )
        }
    }

    fun getOrder(orderId: String, callback: (Result<CinemaOrder>) -> Unit) {
        execute("/api/orders/status/$orderId", "GET", null, true) { result ->
            result.fold(
                onSuccess = { callback(Result.success(it.toOrder(orderId))) },
                onFailure = { callback(Result.failure(it)) },
            )
        }
    }

    fun getTickets(callback: (Result<List<CinemaTicket>>) -> Unit) {
        execute("/api/tickets", "GET", null, true) { result ->
            result.fold(
                onSuccess = { json ->
                    val items = json.optJSONArray("tickets").toObjectList { item ->
                        CinemaTicket(
                            ticketCode = item.optString("ticketCode"),
                            title = item.optString("title"),
                            cinema = item.optString("cinema"),
                            showtime = item.optString("showtime"),
                            seats = item.optString("seats"),
                            amount = item.optLong("amount"),
                            paymentMethod = item.optString("paymentMethod"),
                            ticketQr = item.optString("ticketQr"),
                            bookingDate = item.optString("bookingDate"),
                        )
                    }
                    callback(Result.success(items))
                },
                onFailure = { callback(Result.failure(it)) },
            )
        }
    }

    fun getPendingCounterOrders(callback: (Result<List<PendingCounterOrder>>) -> Unit) {
        execute("/api/staff/orders", "GET", null, true) { result ->
            result.fold(
                onSuccess = { json ->
                    val items = json.optJSONArray("orders").toObjectList { item ->
                        PendingCounterOrder(
                            orderId = item.optString("orderId"),
                            customerName = item.optString("customerName"),
                            email = item.optString("email"),
                            title = item.optString("title"),
                            cinema = item.optString("cinema"),
                            showtime = item.optString("showtime"),
                            seats = item.optString("seats"),
                            amount = item.optLong("amount"),
                            bookingDate = item.optString("bookingDate"),
                        )
                    }
                    callback(Result.success(items))
                },
                onFailure = { callback(Result.failure(it)) },
            )
        }
    }

    fun confirmCounterPayment(orderId: String, callback: (Result<Unit>) -> Unit) {
        execute("/api/staff/orders/$orderId/confirm-counter", "POST", JSONObject(), true) { result ->
            callback(result.map { Unit })
        }
    }

    fun verifyTicket(ticketQr: String, callback: (Result<TicketCheckResult>) -> Unit) {
        execute("/api/staff/tickets/verify", "POST", JSONObject().put("ticketQr", ticketQr), true) { result ->
            callback(result.map { json ->
                TicketCheckResult(
                    valid = json.optBoolean("valid"),
                    alreadyChecked = json.optBoolean("alreadyChecked"),
                    message = json.optString("message"),
                    ticketCode = json.optString("ticketCode"),
                    title = json.optString("title"),
                    cinema = json.optString("cinema"),
                    bookingDate = json.optString("bookingDate"),
                    showtime = json.optString("showtime"),
                    seats = json.optString("seats"),
                )
            })
        }
    }

    fun getAccess(callback: (Result<Pair<Boolean, Boolean>>) -> Unit) {
        execute("/api/me", "GET", null, true) { result ->
            callback(result.map { it.optBoolean("staff") to it.optBoolean("admin") })
        }
    }

    fun bootstrapAdmin(code: String, callback: (Result<Unit>) -> Unit) {
        execute("/api/bootstrap/admin", "POST", JSONObject().put("code", code), true) { callback(it.map { Unit }) }
    }

    fun grantStaff(email: String, callback: (Result<Unit>) -> Unit) {
        execute("/api/admin/staff", "POST", JSONObject().put("email", email), true) { callback(it.map { Unit }) }
    }

    fun getUnavailableSeats(
        title: String,
        cinema: String,
        showtime: String,
        bookingDate: String,
        callback: (Result<Set<String>>) -> Unit,
    ) {
        val charset = Charsets.UTF_8.name()
        val query = "title=${URLEncoder.encode(title, charset)}" +
            "&cinema=${URLEncoder.encode(cinema, charset)}" +
            "&showtime=${URLEncoder.encode(showtime, charset)}" +
            "&bookingDate=${URLEncoder.encode(bookingDate, charset)}"
        execute("/api/seats?$query", "GET", null, false) { result ->
            callback(result.map { json ->
                val seats = json.optJSONArray("unavailableSeats") ?: JSONArray()
                (0 until seats.length()).map { seats.optString(it) }.toSet()
            })
        }
    }

    private fun execute(
        path: String,
        method: String,
        body: JSONObject?,
        requireAuth: Boolean,
        callback: (Result<JSONObject>) -> Unit,
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (requireAuth && user == null) {
            callback(Result.failure(IllegalStateException("Vui lòng đăng nhập để tiếp tục.")))
            return
        }
        val send = { token: String? ->
            val requestBuilder = Request.Builder().url(baseUrl + path)
                .header("Accept", "application/json")
            if (token != null) requestBuilder.header("Authorization", "Bearer $token")
            when (method) {
                "POST" -> requestBuilder.post((body ?: JSONObject()).toString().toRequestBody(jsonType))
                else -> requestBuilder.get()
            }
            client.newCall(requestBuilder.build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    mainHandler.post { callback(Result.failure(e)) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val text = it.body?.string().orEmpty()
                        val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                        mainHandler.post {
                            if (it.isSuccessful && json.optBoolean("success", true)) {
                                callback(Result.success(json))
                            } else {
                                callback(Result.failure(IllegalStateException(
                                    json.optString("message").ifBlank { "Yêu cầu không thành công (${it.code})." }
                                )))
                            }
                        }
                    }
                }
            })
        }

        if (user == null) {
            send(null)
        } else {
            user.getIdToken(false).addOnCompleteListener { task ->
                val token = task.result?.token
                if (task.isSuccessful && token != null) send(token)
                else callback(Result.failure(task.exception ?: IllegalStateException("Không lấy được phiên đăng nhập.")))
            }
        }
    }

    private fun JSONObject.toOrder(defaultId: String) = CinemaOrder(
        orderId = optString("orderId", defaultId),
        status = optString("status"),
        amount = optLong("amount"),
        paymentUrl = optString("paymentUrl"),
        ticketCode = optString("ticketCode"),
        ticketQr = optString("ticketQr"),
        title = optString("title"),
        cinema = optString("cinema"),
        showtime = optString("showtime"),
        seats = optString("seats"),
        bookingDate = optString("bookingDate"),
    )

    private fun <T> JSONArray?.toObjectList(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(map) }
    }
}
