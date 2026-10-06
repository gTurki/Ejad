# Ejad Platform

Ejad (إيجاد) is a lost-and-found platform that connects people who lost items with staff at public places (malls, airports, metro stations, universities) who found them. It starts in Riyadh and is built to expand across Saudi Arabia.

## Features

- **Lost & found reports**: users report lost items, and staff register items found at their location.
- **AI image analysis**: upload a photo and the AI fills in the item details (category, color, brand, description) instead of typing them.
- **AI matching**: the AI compares lost and found reports and suggests matches with a similarity score and a reason.
- **Notifications**: users and staff are notified about new reports, suggested matches, and confirmed matches.
- **Staff verification**: staff accounts must be verified by an admin before they become active.

## Tech Stack

- Java 17+ and Spring Boot 3
- Spring Data JPA with MySQL
- Lombok and Jakarta Validation
- AI API for image analysis and matching

## Data Model

| Entity | Description |
|---|---|
| `BaseAccount` | Shared account fields, inherited by User, Staff and Admin |
| `User` | Person who reports a lost item |
| `Staff` | Employee at a location who registers found items |
| `Admin` | Manages the platform and verifies staff |
| `Location` | Place where items are lost or found (mall, airport, etc.) |
| `Category` | Item category (phones, wallets, bags, etc.) |
| `Report` | A lost or found item report |
| `ReportMatch` | AI-suggested match between a lost report and a found report |
| `Notification` | Message sent to a user or staff member |

## Project Structure

```
com.example.ejadwebapplication
├── Advice       # Global exception handling
├── Api          # ApiException and ApiResponse
├── Config       # DataSeeder
├── Controller   # REST endpoints
├── DTOIN        # Request bodies with validation
├── DTOOUT       # Response bodies
├── Enums        # LocationType, ReportType, ReportStatus, etc.
├── Model        # JPA entities
├── Repository   # Database access
└── Service      # Business logic
```

## Getting Started

1. Clone the repository:
   ```
   git clone https://github.com/gTurki/Ejad-WebApplication.git
   ```
2. Create a MySQL database and set the connection details in `src/main/resources/application.properties`.
3. Add your AI API key to the configuration.
4. Run the application from your IDE, or with:
   ```
   ./mvnw spring-boot:run
   ```

On first run, the `DataSeeder` fills the database with sample data for testing.

## Test Data

| Role | Username | Password | Notes |
|---|---|---|---|
| Admin | `admin` | `Admin1234` | |
| User | `sara` | `Sara1234` | |
| User | `fahad` | `Fahad1234` | |
| Staff | `khalid.staff` | `Khalid1234` | Verified, King Khalid Airport |
| Staff | `noura.staff` | `Noura1234` | Not verified, Al-Hamra mall |

The seeder also adds 4 locations in Riyadh. These accounts are for development only.

## API: Accounts & Locations

All endpoints start with `/api/v1`. Each resource supports `GET /get`, `GET /get/{id}`, `POST /add`, `PUT /update/{id}` and `DELETE /delete/{id}`.

| Method | Endpoint | Description |
|---|---|---|
| `PUT` | `/admin/{adminId}/verify-staff/{staffId}` | Admin verifies a staff account |
| `GET` | `/staff/unverified` | List staff waiting for verification |
| `GET` | `/staff/location/{locationId}` | List staff at a location |
| `GET` | `/location/city/{city}` | List locations in a city |
| `GET` | `/location/type/{type}` | List locations by type (e.g. `MALL`, `AIRPORT`) |

Endpoints for reports, matching and notifications will be added as those modules are completed.

## Team

| Member | Responsibility |
|---|---|
| Amira | Accounts (User, Staff, Admin), Locations, DataSeeder, AI image, External API|
| Fajr | Reports, Categories |
| Turki | ReportMatch, Notifications, AI report matching and AI service |

## Extra Endpoints

| Member | Endpoint |
|---|---|
| Amira | 10 endpoints |
| Location | /geocode/{id} |
|  | /nearby |
|  | /reverse-geocode |
| Fajr | ...... |
|  | ...... |
|  | ...... |
| Turki |...... |
|  | ...... |
|  | ...... |

