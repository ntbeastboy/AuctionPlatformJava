# Auction Platform Frontend (JavaFX)

JavaFX Desktop Application cho Auction Platform - với Network & Database Integration

## 🎯 Overview

JavaFX Desktop client đã được refactor để:
- ✅ Kết nối tới Spring Boot backend qua REST API
- ✅ Sử dụng PostgreSQL database thay vì in-memory storage
- ✅ Hỗ trợ real-time bidding
- ✅ Quản lý người dùng và auction

## 🏗️ Architecture

```
┌──────────────────────────┐
│   JavaFX Desktop UI      │
│  (Controllers + FXML)    │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│   Network Services       │
│ - NetworkUserService     │
│ - NetworkItemService     │
│ - NetworkBidService      │
└────────────┬─────────────┘
             │ HTTP REST
             ▼
┌──────────────────────────┐
│  Spring Boot Backend     │
│   (8080/api)             │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│  PostgreSQL Database     │
│   (5432)                 │
└──────────────────────────┘
```

## ✅ Features

- User login/registration
- View auction items
- Place bids
- Create items (sellers)
- View bid history
- Real-time price updates

## 🚀 Quick Start

### Start Backend (Required)
```bash
cd ..
docker-compose up -d
```

### Run Frontend
```bash
./gradlew.bat run
```

## 📚 Full Documentation

See [README.md](./README.md) for detailed guides
