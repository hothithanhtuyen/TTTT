package com.example.doan.ui.cinema

import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.doan.model.BookingDraft
import com.example.doan.model.BookingRules
import com.example.doan.model.CinemaCatalogue
import com.example.doan.model.CinemaMovie
import com.example.doan.model.CinemaOrder
import com.example.doan.model.CinemaTicket
import com.example.doan.model.TicketCheckResult
import com.example.doan.ui.cinema.CinemaTheme
import com.example.doan.utils.CinemaApi
import com.example.doan.utils.QRCodeUtils
import com.google.firebase.auth.FirebaseAuth
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class CinemaPage { HOME, MOVIE, SEATS, CHECKOUT, ORDER, TICKETS, ACCOUNT, STAFF }

private val CinemaRed = Color(0xFFFF5364)
private val CinemaGold = Color(0xFFFFB36B)
private val CinemaBg = Color(0xFF0B0D12)
private val CinemaSurface = Color(0xFF151821)
private val CinemaMuted = Color(0xFF9EA3AF)

@Composable
fun CinemaApp() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var page by remember { mutableStateOf(CinemaPage.HOME) }
    var selectedMovie by remember { mutableStateOf(CinemaCatalogue.movies.first()) }
    var movieSearch by remember { mutableStateOf("") }
    var bookingDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var province by remember { mutableStateOf("Đà Nẵng") }
    var cinema by remember { mutableStateOf(CinemaCatalogue.cinemas(province).first()) }
    var showtime by remember { mutableStateOf("") }
    var selectedSeats by remember { mutableStateOf(emptySet<String>()) }
    var unavailableSeats by remember { mutableStateOf(emptySet<String>()) }
    var seatLoadMessage by remember { mutableStateOf("") }
    var order by remember { mutableStateOf<CinemaOrder?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var tickets by remember { mutableStateOf(emptyList<CinemaTicket>()) }
    var staffMessage by remember { mutableStateOf("") }
    var ticketCheck by remember { mutableStateOf<TicketCheckResult?>(null) }
    var isStaff by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
            if (it.currentUser == null) {
                isStaff = false
                isAdmin = false
            } else {
                CinemaApi.getAccess { result ->
                    result.onSuccess { (staff, admin) ->
                        isStaff = staff
                        isAdmin = admin
                    }
                }
            }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    DisposableEffect(page, order?.orderId) {
        val currentOrder = order
        if (page != CinemaPage.ORDER || currentOrder == null || currentOrder.status == "PAID") {
            onDispose { }
        } else {
            val handler = Handler(Looper.getMainLooper())
            var stopped = false
            val orderId = currentOrder.orderId
            val polling = object : Runnable {
                override fun run() {
                    if (stopped) return
                    CinemaApi.getOrder(orderId) { result ->
                        result.onSuccess { latest ->
                            order = latest
                            if (latest.status == "PENDING_VNPAY") {
                                handler.postDelayed(this, 3_500)
                            }
                        }.onFailure { message = it.message.orEmpty() }
                    }
                }
            }
            handler.postDelayed(polling, 2_000)
            onDispose {
                stopped = true
                handler.removeCallbacks(polling)
            }
        }
    }

    LaunchedEffect(page, selectedMovie.id, cinema, showtime, bookingDate) {
        if (page == CinemaPage.SEATS && showtime.isNotBlank()) {
            seatLoadMessage = "Đang cập nhật sơ đồ ghế…"
            unavailableSeats = emptySet()
            CinemaApi.getUnavailableSeats(selectedMovie.title, cinema, showtime, bookingDate) { result ->
                result.onSuccess {
                    unavailableSeats = it
                    seatLoadMessage = "Ghế được giữ trực tiếp từ hệ thống đặt vé."
                }.onFailure {
                    unavailableSeats = emptySet()
                    seatLoadMessage = "Chưa tải được ghế đã đặt. Máy chủ sẽ kiểm tra lại khi tạo đơn."
                }
            }
        }
    }

    val showBottomBar = page == CinemaPage.HOME || page == CinemaPage.TICKETS || page == CinemaPage.ACCOUNT
    Scaffold(
        containerColor = CinemaBg,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color(0xFF11141B), tonalElevation = 0.dp) {
                    listOf(
                        Triple(CinemaPage.HOME, "Khám phá", Icons.Filled.Home),
                        Triple(CinemaPage.TICKETS, "Vé của tôi", Icons.Filled.ConfirmationNumber),
                        Triple(CinemaPage.ACCOUNT, "Tài khoản", Icons.Filled.AccountCircle),
                    ).forEach { (target, label, icon) ->
                        NavigationBarItem(
                            selected = page == target,
                            onClick = {
                                message = ""
                                page = target
                                if (target == CinemaPage.TICKETS && currentUser != null) {
                                    busy = true
                                    CinemaApi.getTickets { result ->
                                        busy = false
                                        result.onSuccess { tickets = it }.onFailure { message = it.message.orEmpty() }
                                    }
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = CinemaRed,
                                selectedTextColor = Color.White,
                                indicatorColor = Color(0xFF2A1A20),
                                unselectedIconColor = CinemaMuted,
                                unselectedTextColor = CinemaMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when (page) {
            CinemaPage.HOME -> HomePage(
                modifier = Modifier.padding(innerPadding),
                userName = currentUser?.displayName,
                search = movieSearch,
                onSearch = { movieSearch = it },
                onAccount = { page = CinemaPage.ACCOUNT },
                onMovie = { movie ->
                    selectedMovie = movie
                    bookingDate = LocalDate.now().toString()
                    province = "Đà Nẵng"
                    cinema = CinemaCatalogue.cinemas(province).first()
                    showtime = ""
                    message = ""
                    page = CinemaPage.MOVIE
                },
            )
            CinemaPage.MOVIE -> MoviePage(
                movie = selectedMovie,
                province = province,
                cinema = cinema,
                showtime = showtime,
                bookingDate = bookingDate,
                onBack = { page = CinemaPage.HOME },
                onProvince = { value ->
                    province = value
                    cinema = CinemaCatalogue.cinemas(value).first()
                    showtime = ""
                },
                onCinema = { cinema = it; showtime = "" },
                onDate = { bookingDate = it; showtime = "" },
                onShowtime = { showtime = it },
                onChooseSeats = {
                    if (showtime.isBlank()) message = "Hãy chọn suất chiếu trước."
                    else {
                        message = ""
                        selectedSeats = emptySet()
                        page = CinemaPage.SEATS
                    }
                },
                message = message,
            )
            CinemaPage.SEATS -> SeatPage(
                movie = selectedMovie,
                bookingDate = bookingDate,
                cinema = cinema,
                showtime = showtime,
                selectedSeats = selectedSeats,
                unavailableSeats = unavailableSeats,
                loadMessage = seatLoadMessage,
                onBack = { page = CinemaPage.MOVIE },
                onToggleSeat = { seat ->
                    selectedSeats = if (seat in selectedSeats) selectedSeats - seat else selectedSeats + seat
                },
                onContinue = {
                    if (selectedSeats.isEmpty()) message = "Chọn ít nhất một ghế để tiếp tục."
                    else {
                        message = ""
                        page = CinemaPage.CHECKOUT
                    }
                },
                message = message,
            )
            CinemaPage.CHECKOUT -> CheckoutPage(
                movie = selectedMovie,
                bookingDate = bookingDate,
                province = province,
                cinema = cinema,
                showtime = showtime,
                seats = selectedSeats.toList().sorted(),
                userEmail = currentUser?.email,
                busy = busy,
                message = message,
                onBack = { page = CinemaPage.SEATS },
                onSignIn = { message = "Hãy đăng nhập để giữ đơn đặt vé."; page = CinemaPage.ACCOUNT },
                onCreateOrder = { name ->
                    val user = auth.currentUser
                    if (user == null) {
                        message = "Vui lòng đăng nhập trước khi đặt vé."
                        page = CinemaPage.ACCOUNT
                    } else {
                        busy = true
                        message = ""
                        val draft = BookingDraft(selectedMovie, bookingDate, province, cinema, showtime, selectedSeats.toList().sorted(), name)
                        CinemaApi.createOrder(draft) { result ->
                            busy = false
                            result.onSuccess {
                                order = it
                                page = CinemaPage.ORDER
                            }.onFailure { message = it.message.orEmpty() }
                        }
                    }
                },
            )
            CinemaPage.ORDER -> OrderPage(
                order = order,
                busy = busy,
                message = message,
                onBack = { page = CinemaPage.HOME },
                onOpenPayment = {
                    val url = order?.paymentUrl.orEmpty()
                    if (url.isNotBlank()) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }.onFailure { message = "Không mở được trang VNPAY." }
                    }
                },
                onRefresh = {
                    val id = order?.orderId ?: return@OrderPage
                    busy = true
                    CinemaApi.getOrder(id) { result ->
                        busy = false
                        result.onSuccess { order = it }.onFailure { message = it.message.orEmpty() }
                    }
                },
                onMyTickets = {
                    page = CinemaPage.TICKETS
                    CinemaApi.getTickets { result -> result.onSuccess { tickets = it }.onFailure { message = it.message.orEmpty() } }
                },
            )
            CinemaPage.TICKETS -> TicketsPage(
                modifier = Modifier.padding(innerPadding),
                tickets = tickets,
                busy = busy,
                message = if (currentUser == null) "Đăng nhập để xem vé đã mua." else message,
                onRefresh = {
                    if (currentUser == null) page = CinemaPage.ACCOUNT else {
                        busy = true
                        CinemaApi.getTickets { result ->
                            busy = false
                            result.onSuccess { tickets = it }.onFailure { message = it.message.orEmpty() }
                        }
                    }
                },
                onSignIn = { page = CinemaPage.ACCOUNT },
            )
            CinemaPage.ACCOUNT -> AccountPage(
                modifier = Modifier.padding(innerPadding),
                userEmail = currentUser?.email,
                isStaff = isStaff,
                isAdmin = isAdmin,
                busy = busy,
                message = message,
                onStaff = { message = ""; staffMessage = ""; page = CinemaPage.STAFF },
                onSignOut = {
                    auth.signOut()
                    message = "Bạn đã đăng xuất."
                },
                onAuth = { email, password, displayName, createAccount ->
                    busy = true
                    message = ""
                    val task = if (createAccount) {
                        auth.createUserWithEmailAndPassword(email, password)
                    } else {
                        auth.signInWithEmailAndPassword(email, password)
                    }
                    task.addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) {
                            if (createAccount && displayName.isNotBlank()) {
                                auth.currentUser?.updateProfile(
                                    com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(displayName).build(),
                                )?.addOnCompleteListener { page = CinemaPage.HOME }
                            } else page = CinemaPage.HOME
                        } else message = result.exception?.localizedMessage ?: "Không thể đăng nhập."
                    }
                },
                onResetPassword = { email ->
                    if (email.isBlank()) message = "Nhập email để nhận liên kết đặt lại mật khẩu."
                    else {
                        busy = true
                        auth.sendPasswordResetEmail(email).addOnCompleteListener { result ->
                            busy = false
                            message = if (result.isSuccessful) "Đã gửi hướng dẫn đặt lại mật khẩu." else result.exception?.localizedMessage.orEmpty()
                        }
                    }
                },
                onBootstrapAdmin = { code ->
                    busy = true
                    message = ""
                    CinemaApi.bootstrapAdmin(code) { result ->
                        result.fold(
                            onSuccess = {
                                auth.currentUser?.getIdToken(true)?.addOnCompleteListener {
                                    CinemaApi.getAccess { access ->
                                        busy = false
                                        access.onSuccess { (staff, admin) ->
                                            isStaff = staff
                                            isAdmin = admin
                                            message = "Tài khoản admin đã được kích hoạt."
                                        }.onFailure { message = it.message.orEmpty() }
                                    }
                                } ?: run { busy = false; message = "Không làm mới được phiên đăng nhập." }
                            },
                            onFailure = { busy = false; message = it.message.orEmpty() },
                        )
                    }
                },
                onGrantStaff = { email ->
                    busy = true
                    CinemaApi.grantStaff(email.trim()) { result ->
                        busy = false
                        message = result.fold(
                            onSuccess = { "Đã cấp quyền nhân viên cho $email." },
                            onFailure = { it.message.orEmpty() },
                        )
                    }
                },
            )
            CinemaPage.STAFF -> StaffPage(
                busy = busy,
                message = staffMessage,
                ticketCheck = ticketCheck,
                onBack = { page = CinemaPage.ACCOUNT },
                onVerifyTicket = { qr ->
                    busy = true
                    staffMessage = ""
                    ticketCheck = null
                    CinemaApi.verifyTicket(qr) { result ->
                        busy = false
                        result.onSuccess {
                            ticketCheck = it
                            staffMessage = it.message
                        }.onFailure {
                            ticketCheck = null
                            staffMessage = it.message.orEmpty()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun HomePage(
    modifier: Modifier,
    userName: String?,
    search: String,
    onSearch: (String) -> Unit,
    onAccount: () -> Unit,
    onMovie: (CinemaMovie) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("RẠP PHIM", color = CinemaRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Xin chào${userName?.let { ", ${it.substringBefore(' ')}"}.orEmpty()}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onAccount, modifier = Modifier.background(CinemaSurface, CircleShape)) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = "Tài khoản", tint = Color.White)
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
            ) {
                Box(Modifier.fillMaxWidth().height(230.dp)) {
                    AsyncImage(
                        model = CinemaCatalogue.movies.first().posterUrl,
                        contentDescription = "Phim nổi bật ${CinemaCatalogue.movies.first().title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text("PHIM NỔI BẬT", color = CinemaGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                        Text("Đêm nay, rạp là của bạn.", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Chọn phim · Chọn ghế · Đặt vé trong vài chạm", color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
                    }
                }
            }
        }
        item { OutlinedField("Tìm phim theo tên hoặc thể loại", search, onSearch, singleLine = true) }
        item { SectionTitle(if (search.isBlank()) "Đang chiếu" else "Kết quả tìm kiếm", "Chọn bộ phim tiếp theo của bạn") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 2.dp)) {
                val matchingMovies = CinemaCatalogue.movies.filter {
                    search.isBlank() || it.title.contains(search.trim(), ignoreCase = true) || it.genre.contains(search.trim(), ignoreCase = true)
                }
                items(matchingMovies) { movie -> PosterCard(movie, onClick = { onMovie(movie) }) }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D27))) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("RẠP GẦN BẠN", color = CinemaMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Đà Nẵng · chọn rạp khi đặt vé", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = CinemaMuted)
                }
            }
        }
    }
}

@Composable
private fun PosterCard(movie: CinemaMovie, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CinemaSurface)) {
            Box(Modifier.fillMaxWidth().height(218.dp)) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
                    color = Color(0xCC101218), shape = RoundedCornerShape(8.dp),
                ) { Text(movie.rating, color = CinemaGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(movie.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
        Text(movie.duration, color = CinemaMuted, fontSize = 11.sp)
    }
}

@Composable
private fun MoviePage(
    movie: CinemaMovie,
    province: String,
    cinema: String,
    showtime: String,
    bookingDate: String,
    onBack: () -> Unit,
    onProvince: (String) -> Unit,
    onCinema: (String) -> Unit,
    onDate: (String) -> Unit,
    onShowtime: (String) -> Unit,
    onChooseSeats: () -> Unit,
    message: String,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(370.dp)) {
            AsyncImage(model = movie.posterUrl, contentDescription = movie.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, CinemaBg))))
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(14.dp).background(Color(0xAA11141B), CircleShape)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text(movie.tagline, color = CinemaGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(movie.title, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(16.dp))
                    Text("${movie.rating}  ·  ${movie.duration}", color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                    Text(movie.genre, color = Color.White.copy(alpha = .72f), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(movie.synopsis, color = Color(0xFFD0D3DA), fontSize = 14.sp, lineHeight = 22.sp)
            HorizontalDivider(color = Color(0xFF2B2F3A))
            SectionTitle("Chọn khu vực", "")
            ChoiceRow(CinemaCatalogue.provinces, province, onProvince)
            SectionTitle("Rạp chiếu", "")
            ChoiceRow(CinemaCatalogue.cinemas(province), cinema, onCinema)
            SectionTitle("Chọn ngày", "Lịch chiếu trong 7 ngày tới")
            DateChoiceRow(bookingDate, onDate)
            SectionTitle("Suất chiếu", LocalDate.parse(bookingDate).format(DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale("vi", "VN"))))
            ChoiceRow(CinemaCatalogue.showtimes, showtime, onShowtime)
            if (message.isNotBlank()) InlineMessage(message, isError = true)
            Button(
                onClick = onChooseSeats,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CinemaRed),
            ) {
                Text("Chọn ghế", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.ArrowForward, contentDescription = null)
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun SeatPage(
    movie: CinemaMovie,
    bookingDate: String,
    cinema: String,
    showtime: String,
    selectedSeats: Set<String>,
    unavailableSeats: Set<String>,
    loadMessage: String,
    onBack: () -> Unit,
    onToggleSeat: (String) -> Unit,
    onContinue: () -> Unit,
    message: String,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
        BackHeader("Chọn ghế", onBack)
        Spacer(Modifier.height(14.dp))
        Text(movie.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("${LocalDate.parse(bookingDate).format(DateTimeFormatter.ofPattern("dd/MM"))}  ·  $cinema  ·  $showtime", color = CinemaMuted, fontSize = 13.sp)
        Spacer(Modifier.height(25.dp))
        Box(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(100)).background(Brush.horizontalGradient(listOf(Color.Transparent, CinemaGold, Color.Transparent))),
        )
        Text("MÀN HÌNH", modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, color = CinemaMuted, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ('A'..'F').forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.toString(), color = CinemaMuted, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
                    (1..5).forEach { number ->
                        val seat = "$row$number"
                        val occupied = seat in unavailableSeats
                        val selected = seat in selectedSeats
                        val color = when {
                            occupied -> Color(0xFF3B404A)
                            selected -> CinemaRed
                            else -> Color(0xFF26303A)
                        }
                        Box(
                            Modifier.size(43.dp, 38.dp)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                                .background(color)
                                .then(if (!occupied) Modifier.clickable { onToggleSeat(seat) } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(number.toString(), color = if (occupied) Color(0xFF828792) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SeatLegend(Color(0xFF26303A), "Trống")
            SeatLegend(CinemaRed, "Đang chọn")
            SeatLegend(Color(0xFF3B404A), "Đã đặt")
        }
        if (loadMessage.isNotBlank()) Text(loadMessage, color = CinemaMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(18.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CinemaSurface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${selectedSeats.size} ghế đã chọn", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(selectedSeats.sorted().joinToString().ifBlank { "Chưa chọn ghế" }, color = CinemaMuted, fontSize = 12.sp)
                }
                Text(formatMoney(BookingRules.totalPrice(selectedSeats.size)), color = CinemaGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (message.isNotBlank()) InlineMessage(message, isError = true, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = CinemaRed)) {
            Text("Tiếp tục thanh toán", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DateChoiceRow(selectedDate: String, onSelect: (String) -> Unit) {
    val today = remember { LocalDate.now() }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        (0L..6L).forEach { offset ->
            val date = today.plusDays(offset)
            val isoDate = date.toString()
            val selected = isoDate == selectedDate
            val label = if (offset == 0L) "Hôm nay\n${date.format(DateTimeFormatter.ofPattern("dd/MM"))}"
                else "${date.format(DateTimeFormatter.ofPattern("EEE", Locale("vi", "VN")))}\n${date.format(DateTimeFormatter.ofPattern("dd/MM"))}"
            Surface(
                modifier = Modifier.clickable { onSelect(isoDate) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) Color(0xFF351D25) else CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) CinemaRed else Color(0xFF30343E)),
            ) {
                Text(label, color = if (selected) Color.White else CinemaMuted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun CheckoutPage(
    movie: CinemaMovie,
    bookingDate: String,
    province: String,
    cinema: String,
    showtime: String,
    seats: List<String>,
    userEmail: String?,
    busy: Boolean,
    message: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onCreateOrder: (String) -> Unit,
) {
    var customerName by remember(userEmail) { mutableStateOf(userEmail?.substringBefore('@').orEmpty()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        BackHeader("Xác nhận đặt vé", onBack)
        Spacer(Modifier.height(18.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CinemaSurface)) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = movie.posterUrl, contentDescription = null, modifier = Modifier.size(80.dp, 116.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(cinema, color = CinemaMuted, fontSize = 12.sp)
                    Text("$province · ${LocalDate.parse(bookingDate).format(DateTimeFormatter.ofPattern("dd/MM"))} · $showtime", color = CinemaMuted, fontSize = 12.sp)
                    Text("Ghế ${seats.joinToString()}", color = CinemaGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (userEmail == null) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF24191F))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Đăng nhập để giữ chỗ", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Đơn và vé sẽ được lưu an toàn theo tài khoản của bạn.", color = CinemaMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                    TextButton(onClick = onSignIn, modifier = Modifier.align(Alignment.End)) { Text("Đăng nhập", color = CinemaRed) }
                }
            }
        } else {
            OutlinedField("Tên người đặt", customerName, { customerName = it }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Text("Email nhận vé: $userEmail", color = CinemaMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CinemaSurface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PriceLine("${seats.size} vé × ${formatMoney(BookingRules.TICKET_PRICE_VND)}", formatMoney(seats.size * BookingRules.TICKET_PRICE_VND))
                PriceLine("Phí đặt vé", formatMoney(BookingRules.BOOKING_FEE_VND))
                HorizontalDivider(color = Color(0xFF353945), modifier = Modifier.padding(vertical = 4.dp))
                PriceLine("Tổng thanh toán", formatMoney(BookingRules.totalPrice(seats.size)), isTotal = true)
            }
        }
        if (message.isNotBlank()) InlineMessage(message, isError = true, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (customerName.isBlank()) onCreateOrder("") else onCreateOrder(customerName.trim()) },
            enabled = !busy && userEmail != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CinemaRed),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Xác nhận thanh toán · ${formatMoney(BookingRules.totalPrice(seats.size))}", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OrderPage(
    order: CinemaOrder?,
    busy: Boolean,
    message: String,
    onBack: () -> Unit,
    onOpenPayment: () -> Unit,
    onRefresh: () -> Unit,
    onMyTickets: () -> Unit,
) {
    val current = order
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BackHeader("Trạng thái đơn", onBack)
        Spacer(Modifier.height(20.dp))
        if (current == null) {
            CircularProgressIndicator(color = CinemaRed)
            Text("Đang tạo đơn đặt vé…", color = Color.White, modifier = Modifier.padding(top = 14.dp))
        } else {
            val paid = current.status == "PAID"
            val codeContent = if (paid) current.ticketQr else current.paymentUrl
            val qr = remember(codeContent) { QRCodeUtils.generateQRCode(codeContent, 420, 420) }
            Spacer(Modifier.height(10.dp))
            Icon(
                if (paid) Icons.Filled.CheckCircle else Icons.Filled.ConfirmationNumber,
                contentDescription = null,
                tint = if (paid) Color(0xFF4ED39A) else CinemaGold,
                modifier = Modifier.size(48.dp),
            )
            Text(
                when (current.status) {
                    "PAID" -> "Vé đã được phát hành"
                    "PENDING_VNPAY" -> "Chờ VNPAY xác nhận"
                    "EXPIRED" -> "Đơn đã hết hạn"
                    else -> "Đơn đang được xử lý"
                },
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text("Mã đơn ${current.orderId.take(8)}", color = CinemaMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Card(
                modifier = Modifier.padding(top = 22.dp).width(236.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                if (qr != null) {
                    Image(
                        qr.asImageBitmap(),
                        contentDescription = if (paid) "QR vé đã thanh toán" else "QR thanh toán VNPAY",
                        modifier = Modifier.fillMaxWidth().padding(15.dp).aspectRatio(1f),
                    )
                } else {
                    Text(
                        if (paid) "Máy chủ chưa trả mã QR vé. Hãy kiểm tra lại trạng thái." else "Chưa nhận được QR thanh toán từ máy chủ.",
                        color = CinemaMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
            Text(
                if (paid) "Đưa QR vé này cho nhân viên soát vé." else "Quét QR để mở thanh toán VNPAY hoặc mở cổng thanh toán bên dưới.",
                color = CinemaMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
            Spacer(Modifier.height(16.dp))
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CinemaSurface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(current.title.ifBlank { "Đơn đặt vé" }, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${current.cinema} · ${current.showtime}", color = CinemaMuted, fontSize = 12.sp)
                    Text("Ghế ${current.seats}", color = CinemaMuted, fontSize = 12.sp)
                    Text(formatMoney(current.amount), color = CinemaGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!paid && current.paymentUrl.isNotBlank()) {
                Button(onClick = onOpenPayment, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = CinemaRed)) {
                    Text("Mở VNPAY Sandbox", fontWeight = FontWeight.Bold)
                }
            }
            if (paid) {
                Button(onClick = onMyTickets, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = CinemaRed)) {
                    Text("Xem vé của tôi", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.padding(top = 4.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(17.dp), color = CinemaRed, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = null, tint = CinemaRed)
                    Spacer(Modifier.width(7.dp))
                    Text("Kiểm tra trạng thái", color = CinemaRed)
                }
            }
            if (message.isNotBlank()) Text(message, color = CinemaMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Text("QR được tạo từ trạng thái đơn do máy chủ xác nhận.", color = Color(0xFF686E7A), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun TicketsPage(
    modifier: Modifier,
    tickets: List<CinemaTicket>,
    busy: Boolean,
    message: String,
    onRefresh: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VÉ CỦA TÔI", color = CinemaRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Lịch sử đặt vé", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Tải lại", tint = Color.White) }
        }
        Spacer(Modifier.height(16.dp))
        if (busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CinemaRed) }
        else if (tickets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, tint = CinemaMuted, modifier = Modifier.size(54.dp))
                    Text(if (message.isNotBlank()) message else "Chưa có vé nào", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                    if (message.contains("Đăng nhập")) TextButton(onClick = onSignIn) { Text("Đăng nhập", color = CinemaRed) }
                    else Text("Vé đã thanh toán sẽ xuất hiện tại đây.", color = CinemaMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(tickets, key = { it.ticketCode }) { ticket -> TicketCard(ticket) }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: CinemaTicket) {
    val qrContent = ticket.ticketQr
    val qr = remember(qrContent) { QRCodeUtils.generateQRCode(qrContent, 280, 280) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CinemaSurface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ticket.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(ticket.cinema, color = CinemaMuted, fontSize = 12.sp)
                Text("${ticket.bookingDate} · ${ticket.showtime} · Ghế ${ticket.seats}", color = CinemaMuted, fontSize = 12.sp)
                Text(ticket.ticketCode.substringAfter('-', ticket.ticketCode), color = CinemaGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            qr?.let { Image(it.asImageBitmap(), contentDescription = "QR vé ${ticket.ticketCode}", modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(5.dp)) }
        }
    }
}

@Composable
private fun AccountPage(
    modifier: Modifier,
    userEmail: String?,
    isStaff: Boolean,
    isAdmin: Boolean,
    busy: Boolean,
    message: String,
    onStaff: () -> Unit,
    onSignOut: () -> Unit,
    onAuth: (String, String, String, Boolean) -> Unit,
    onResetPassword: (String) -> Unit,
    onBootstrapAdmin: (String) -> Unit,
    onGrantStaff: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bootstrapCode by remember { mutableStateOf("") }
    var staffEmail by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("TÀI KHOẢN", color = CinemaRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("${if (userEmail == null) "Đăng nhập" else "Xin chào"}", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.height(18.dp))
        if (userEmail != null) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CinemaSurface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(42.dp))
                    Text(userEmail, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    Text("Tài khoản được xác thực bởi Firebase Authentication.", color = CinemaMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(if (isAdmin) "Quản trị viên" else if (isStaff) "Nhân viên rạp" else "Tài khoản khách hàng", color = CinemaGold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (!isStaff && !isAdmin) {
                Spacer(Modifier.height(14.dp))
                SectionTitle("Kích hoạt tài khoản admin", "Dùng email riêng cho admin, không dùng tài khoản khách đặt vé.")
                Spacer(Modifier.height(8.dp))
                OutlinedField("Mã kích hoạt admin", bootstrapCode, { bootstrapCode = it }, singleLine = true)
                Button(
                    onClick = { onBootstrapAdmin(bootstrapCode.trim()) },
                    enabled = !busy && bootstrapCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaRed),
                ) { Text("Kích hoạt admin", fontWeight = FontWeight.Bold) }
            }
            if (isAdmin) {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Cấp quyền nhân viên", "Tài khoản nhân viên cần đăng ký trong ứng dụng trước.")
                Spacer(Modifier.height(8.dp))
                OutlinedField("Email nhân viên", staffEmail, { staffEmail = it }, keyboardType = KeyboardType.Email, singleLine = true)
                Button(
                    onClick = { onGrantStaff(staffEmail) },
                    enabled = !busy && staffEmail.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaRed),
                ) { Text("Cấp quyền nhân viên", fontWeight = FontWeight.Bold) }
            }
            if (isStaff || isAdmin) {
                Spacer(Modifier.height(14.dp))
                AccountAction("Khu vực nhân viên", "Tra cứu đơn và xác nhận thanh toán tại quầy", Icons.Filled.AdminPanelSettings, onStaff)
            }
            Spacer(Modifier.height(10.dp))
            AccountAction("Đăng xuất", "Đăng xuất khỏi thiết bị này", Icons.Filled.Logout, onSignOut)
        } else {
            Text("Khám phá phim trước, đăng nhập khi muốn giữ chỗ và lưu vé.", color = CinemaMuted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(18.dp))
            if (createAccount) OutlinedField("Tên hiển thị", displayName, { displayName = it }, singleLine = true)
            Spacer(Modifier.height(if (createAccount) 11.dp else 0.dp))
            OutlinedField("Email", email, { email = it }, keyboardType = KeyboardType.Email, singleLine = true)
            Spacer(Modifier.height(11.dp))
            OutlinedField("Mật khẩu", password, { password = it }, keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(17.dp))
            Button(
                onClick = { onAuth(email.trim(), password, displayName.trim(), createAccount) },
                enabled = !busy && email.isNotBlank() && password.length >= 6,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CinemaRed),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (createAccount) "Tạo tài khoản" else "Đăng nhập", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { createAccount = !createAccount }) {
                    Text(if (createAccount) "Đã có tài khoản? Đăng nhập" else "Tạo tài khoản mới", color = CinemaGold, fontSize = 12.sp)
                }
                if (!createAccount) TextButton(onClick = { onResetPassword(email.trim()) }) { Text("Quên mật khẩu", color = CinemaMuted, fontSize = 12.sp) }
            }
        }
        if (message.isNotBlank()) InlineMessage(message, isError = message.contains("lỗi", true) || message.contains("không", true), modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(20.dp))
        Text("Dữ liệu mật khẩu do Firebase Authentication quản lý; ứng dụng không lưu mật khẩu trong cơ sở dữ liệu phim.", color = Color(0xFF727784), fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun StaffPage(
    busy: Boolean,
    message: String,
    ticketCheck: TicketCheckResult?,
    onBack: () -> Unit,
    onVerifyTicket: (String) -> Unit,
) {
    var ticketQr by remember { mutableStateOf("") }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { scanResult ->
        scanResult.contents?.takeIf(String::isNotBlank)?.let(onVerifyTicket)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("NHÂN VIÊN", color = CinemaRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text("Kiểm tra vé", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (message.isNotBlank()) InlineMessage(message, isError = message.contains("quyền", true) || message.contains("403"))
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CinemaSurface), modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Soát vé bằng QR", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Quét bằng camera. Máy chủ kiểm tra chữ ký và chặn quét lại.", color = CinemaMuted, fontSize = 11.sp)
                Button(
                    onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Quét mã QR vé")
                            setBeepEnabled(false)
                            setOrientationLocked(false)
                        }
                        scanLauncher.launch(options)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaRed),
                ) { Text("Mở camera quét vé", fontWeight = FontWeight.Bold) }
                Text("Hoặc nhập mã QR", color = CinemaMuted, fontSize = 11.sp)
                OutlinedField("Nội dung QR vé", ticketQr, { ticketQr = it }, singleLine = true)
                Button(
                    onClick = { onVerifyTicket(ticketQr.trim()) },
                    enabled = !busy && ticketQr.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaRed),
                ) { Text("Kiểm tra vé", fontWeight = FontWeight.Bold) }
                ticketCheck?.let { checked ->
                    Text("${checked.title} · ${checked.bookingDate} ${checked.showtime}", color = if (checked.valid) Color(0xFF4ED39A) else CinemaGold, fontSize = 12.sp)
                    Text("${checked.cinema} · Ghế ${checked.seats} · ${checked.ticketCode}", color = CinemaMuted, fontSize = 11.sp)
                }
            }
        }
        if (busy) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CinemaRed) }
    }
}

@Composable
private fun ChoiceRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items.forEach { item ->
            val chosen = item == selected
            Surface(
                modifier = Modifier.clickable { onSelect(item) },
                shape = RoundedCornerShape(12.dp),
                color = if (chosen) Color(0xFF351D25) else CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (chosen) CinemaRed else Color(0xFF30343E)),
            ) {
                Text(item, color = if (chosen) Color.White else CinemaMuted, fontSize = 12.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = CinemaMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(42.dp).background(CinemaSurface, CircleShape)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SeatLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(label, color = CinemaMuted, fontSize = 10.sp)
    }
}

@Composable
private fun PriceLine(label: String, value: String, isTotal: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (isTotal) Color.White else CinemaMuted, fontSize = if (isTotal) 14.sp else 12.sp, fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal)
        Text(value, color = if (isTotal) CinemaGold else Color.White, fontSize = if (isTotal) 16.sp else 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OutlinedField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CinemaRed,
            unfocusedBorderColor = Color(0xFF383D48),
            focusedLabelColor = CinemaRed,
            unfocusedLabelColor = CinemaMuted,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = CinemaRed,
        ),
    )
}

@Composable
private fun AccountAction(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CinemaSurface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = CinemaGold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(detail, color = CinemaMuted, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = CinemaMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun InlineMessage(message: String, isError: Boolean, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isError) Color(0xFF291A20) else Color(0xFF17251F)).padding(12.dp),
        color = if (isError) Color(0xFFFFA5AF) else Color(0xFF9EE0BE),
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

private fun formatMoney(amount: Long): String =
    NumberFormat.getIntegerInstance(Locale("vi", "VN")).format(amount) + " đ"
