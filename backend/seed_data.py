"""
Seed Data Script
Populates the database with Da Nang cinemas, TMDB movies, rooms, seats A1-F5,
showtimes, promotions, and default demo accounts.
"""

import hashlib
from datetime import datetime, timedelta
from database import get_db_connection, init_db


def hash_password(password: str) -> str:
    return hashlib.sha256(password.encode("utf-8")).hexdigest()


def seed_all():
    init_db()
    conn = get_db_connection()
    cursor = conn.cursor()

    # 1. Seed Users
    admin_pw = hash_password("admin123")
    user_pw = hash_password("123456")

    cursor.execute("""
        INSERT OR IGNORE INTO users (email, password_hash, full_name, phone, role)
        VALUES 
            ('admin@cinema.com', ?, 'Quản Trị Viên', '0905123456', 'ADMIN'),
            ('a@gmail.com', ?, 'Nguyễn Văn A', '0970419852', 'USER')
    """, (admin_pw, user_pw))

    # 2. Seed Genres
    genres = [
        ("Hành Động", "Phim hành động kịch tính, mãn nhãn"),
        ("Khoa Học Viễn Tưởng", "Thế giới tương lai, vũ trụ và siêu năng lực"),
        ("Tâm Lý - Gia Đình", "Câu chuyện tình cảm, chạm tới cảm xúc"),
        ("Hoạt Hình", "Phim hoạt hình 3D cho gia đình và giới trẻ"),
        ("Hài Kịch", "Hài hước, giải trí sảng khoái"),
        ("Kinh Dị", "Hồi hộp rùng rợn, giật gân")
    ]
    for name, desc in genres:
        cursor.execute("INSERT OR IGNORE INTO genres (name, description) VALUES (?, ?)", (name, desc))

    # 3. Seed Cinemas in Da Nang
    cinemas = [
        ("Rio Liên Chiểu", "403 Tôn Đức Thắng, P. Hòa Minh, Q. Liên Chiểu, TP. Đà Nẵng", "Đà Nẵng", "0236.3768.999", 16.0592, 108.1623),
        ("Starlight Thanh Khê", "Tầng 3-4 TTTM Điện Biên Phủ, P. Thanh Khê Đông, Q. Thanh Khê, TP. Đà Nẵng", "Đà Nẵng", "1900.1722", 16.0654, 108.1921),
        ("Galaxy Thanh Khê", "Tòa nhà Coop Mart, 478 Điện Biên Phủ, P. Thanh Khê Đông, TP. Đà Nẵng", "Đà Nẵng", "1900.2224", 16.0648, 108.1915),
        ("CGV Thanh Khê", "Vĩnh Trung Plaza, 255-257 Hùng Vương, P. Vĩnh Trung, TP. Đà Nẵng", "Đà Nẵng", "1900.6017", 16.0682, 108.2134),
        ("CGV Sơn Trà", "Tầng 3 TTTM Vincom Plaza Ngô Quyền, P. An Hải Bắc, Q. Sơn Trà, TP. Đà Nẵng", "Đà Nẵng", "1900.6017", 16.0711, 108.2325),
        ("CGV Liên Chiểu", "Khu đô thị Kim Long City, Nguyễn Sinh Sắc, Q. Liên Chiểu, TP. Đà Nẵng", "Đà Nẵng", "1900.6017", 16.0621, 108.1589),
        ("Lotte Hải Châu", "Tầng 5 Lotte Mart, 06 Nại Nam, P. Hòa Cường Bắc, Q. Hải Châu, TP. Đà Nẵng", "Đà Nẵng", "0236.3679.555", 16.0354, 108.2268)
    ]

    for name, address, prov, phone, lat, lng in cinemas:
        cursor.execute("""
            INSERT OR IGNORE INTO cinemas (name, address, province, phone, latitude, longitude)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (name, address, prov, phone, lat, lng))

    # 4. Seed Movies
    movies = [
        (
            "Avatar: Dòng Chảy Của Nước",
            "Jake Sully sống cùng gia đình mới được thành lập trên mặt trăng ngoài hành tinh Pandora. Khi một mối đe dọa quen thuộc quay trở lại, Jake phải hợp tác với Neytiri và quân đội của chủng tộc Na'vi để bảo vệ hành tinh của họ.",
            192, "James Cameron", "Sam Worthington, Zoe Saldana, Sigourney Weaver",
            "2022-12-16", "C13", 2,
            "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
            "https://image.tmdb.org/t/p/original/8YFL5QQVPy3AgrEQxNYVSgiPEbe.jpg",
            "https://www.youtube.com/watch?v=d9MyW72ELq0",
            8.8, "NOW_SHOWING"
        ),
        (
            "Mai",
            "Mai là một người phụ nữ làm nghề massage trị liệu chịu nhiều định kiến của xã hội. Cuộc đời cô bắt đầu rực rỡ và cũng đầy biến động khi gặp Dương - một chàng trai đào hoa kém tuổi.",
            131, "Trấn Thành", "Phương Anh Đào, Tuấn Trần, Trấn Thành, Hồng Đào",
            "2024-02-10", "C18", 3,
            "https://image.tmdb.org/t/p/w500/b1p5xI1QYqI8b8L0s0K8X0s1.jpg",
            "https://image.tmdb.org/t/p/original/b1p5xI1QYqI8b8L0s0K8X0s1.jpg",
            "https://www.youtube.com/watch?v=sample",
            8.2, "NOW_SHOWING"
        ),
        (
            "Dune: Hành Tinh Cát - Phần 2",
            "Paul Atreides hợp nhất với Chani và người Fremen trong khi tìm kiếm sự trả thù những kẻ chủ mưu đã hủy hoại gia đình mình, đứng trước sự lựa chọn giữa tình yêu và số phận vũ trụ.",
            166, "Denis Villeneuve", "Timothée Chalamet, Zendaya, Rebecca Ferguson",
            "2024-03-01", "C16", 2,
            "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
            "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s520DRq.jpg",
            "https://www.youtube.com/watch?v=Way9Dexny3w",
            8.9, "NOW_SHOWING"
        ),
        (
            "Lật Mặt 7: Một Điều Ước",
            "Câu chuyện cảm động về bà Hai 73 tuổi cùng 5 người con. Khi người mẹ gặp tai nạn, một cuộc họp gia đình diễn ra để bàn xem ai sẽ là người chăm sóc mẹ già.",
            138, "Lý Hải", "Thanh Hiền, Trương Minh Cường, Đinh Y Nhung",
            "2024-04-26", "K", 3,
            "https://image.tmdb.org/t/p/w500/latmat7.jpg",
            "https://image.tmdb.org/t/p/original/latmat7_banner.jpg",
            "https://www.youtube.com/watch?v=sample",
            8.5, "NOW_SHOWING"
        ),
        (
            "Deadpool & Wolverine",
            "Wade Wilson đang tận hưởng cuộc sống bình dị thì Cơ quan Biến đổi Thời gian (TVA) lôi anh vào một nhiệm vụ mới, buộc anh phải hợp tác cùng Người Sói Wolverine.",
            128, "Shawn Levy", "Ryan Reynolds, Hugh Jackman, Emma Corrin",
            "2024-07-26", "C18", 1,
            "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
            "https://image.tmdb.org/t/p/original/yDHYTfA3R0jFYba16jBB1jv8vEU.jpg",
            "https://www.youtube.com/watch?v=73_1biulkYk",
            8.6, "NOW_SHOWING"
        ),
        (
            "Kung Fu Panda 4",
            "Po được chọn trở thành Thủ lĩnh Tinh thần của Thung lũng Bình Yên và phải huấn luyện một Chiến binh Rồng mới trong khi đối mặt với Tắc Kè Bông xảo quyệt.",
            94, "Mike Mitchell", "Jack Black, Awkwafina, Viola Davis",
            "2024-03-08", "P", 4,
            "https://image.tmdb.org/t/p/w500/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg",
            "https://image.tmdb.org/t/p/original/1XDDXPXGiI8id7MrUxK26VI48ue.jpg",
            "https://www.youtube.com/watch?v=_inKs4eeHiI",
            7.8, "COMING_SOON"
        )
    ]

    for m in movies:
        cursor.execute("""
            INSERT OR IGNORE INTO movies (
                title, description, duration_minutes, director, actors,
                release_date, age_rating, genre_id, poster_url, banner_url,
                trailer_url, rating, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, m)

    # 5. Seed Rooms & Seats for all Cinemas
    cursor.execute("SELECT id FROM cinemas")
    cinema_ids = [r[0] for r in cursor.fetchall()]

    for cid in cinema_ids:
        # Check if rooms exist
        cursor.execute("SELECT COUNT(*) FROM cinema_rooms WHERE cinema_id = ?", (cid,))
        if cursor.fetchone()[0] == 0:
            cursor.execute("INSERT INTO cinema_rooms (cinema_id, name, room_type, total_seats) VALUES (?, 'Phòng chiếu 01', '2D Digital', 30)", (cid,))
            room1_id = cursor.lastrowid
            cursor.execute("INSERT INTO cinema_rooms (cinema_id, name, room_type, total_seats) VALUES (?, 'Phòng chiếu 02 (VIP)', '2D VIP Atmos', 30)", (cid,))
            room2_id = cursor.lastrowid

            for rid in [room1_id, room2_id]:
                # 30 seats: A1 to F5
                rows = ["A", "B", "C", "D", "E", "F"]
                for row in rows:
                    for num in range(1, 6):
                        seat_code = f"{row}{num}"
                        if row in ["A", "B"]:
                            stype = "STANDARD"
                            price = 45000
                        elif row in ["C", "D", "E"]:
                            stype = "VIP"
                            price = 60000
                        else:
                            stype = "COUPLE"
                            price = 80000

                        cursor.execute("""
                            INSERT OR IGNORE INTO seats (room_id, seat_row, seat_number, seat_code, seat_type, base_price)
                            VALUES (?, ?, ?, ?, ?, ?)
                        """, (rid, row, num, seat_code, stype, price))

    # 6. Seed Showtimes for Today and Next 3 Days
    cursor.execute("SELECT id FROM movies WHERE status = 'NOW_SHOWING'")
    movie_ids = [r[0] for r in cursor.fetchall()]

    cursor.execute("SELECT id FROM cinema_rooms")
    all_rooms = [r[0] for r in cursor.fetchall()]

    today = datetime.now()
    dates = [(today + timedelta(days=i)).strftime("%Y-%m-%d") for i in range(4)]
    times = ["09:30", "13:45", "16:30", "19:15", "21:45"]

    for d in dates:
        for mid in movie_ids:
            for rid in all_rooms[:4]: # distribute across first 4 rooms
                for t in times[:3]:
                    cursor.execute("""
                        INSERT OR IGNORE INTO showtimes (movie_id, room_id, show_date, start_time, format_type, price_override)
                        VALUES (?, ?, ?, ?, '2D', NULL)
                    """, (mid, rid, d, t))

    # 7. Seed Promotions
    promos = [
        ("CINEMA10", "Giảm 10% tối đa 30.000đ cho mọi đơn đặt vé", 10, 30000, "2026-01-01", "2026-12-31"),
        ("VNPAY20", "Giảm 20.000đ cho đơn hàng thanh toán qua VNPay", 15, 20000, "2026-01-01", "2026-12-31"),
        ("VIPMEMBER", "Ưu đãi độc quyền 15% cho thành viên VIP", 15, 50000, "2026-01-01", "2026-12-31")
    ]
    for code, desc, pct, max_d, s_d, e_d in promos:
        cursor.execute("""
            INSERT OR IGNORE INTO promotions (code, description, discount_percent, max_discount, start_date, end_date, is_active)
            VALUES (?, ?, ?, ?, ?, ?, 1)
        """, (code, desc, pct, max_d, s_d, e_d))

    conn.commit()
    conn.close()
    print("Database initialization and seed data completed successfully!")


if __name__ == "__main__":
    seed_all()
