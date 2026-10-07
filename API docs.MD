# API Documentation: Used Marketplace

This document outlines the RESTful endpoints available in the application.
Base URL: `http://localhost:8080`

## 🔐 Authentication (`/auth/user`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| Register User | `POST` | `/auth/user/register` | Creates a new user account. Sends verification email. | ❌ No |
| Verify Email | `POST` | `/auth/user/verify` | Validates the 6-digit code sent to user's email. Activates account. | ❌ No |
| Resend Code | `POST` | `/auth/user/resend-verify` | Triggers a new verification email (Rate-limited to 60s). | ❌ No |
| Login | `POST` | `/auth/user/login` | Authenticates user and returns a JWT Token. | ❌ No |
| Forget Password | `POST` | `/auth/user/forget-password` | Initiates password reset flow via email. | ❌ No |
| Reset Password | `POST` | `/auth/user/reset-password` | Sets new password using reset code. | ❌ No |
| Change Password | `PUT` | `/auth/user/change-password` | Updates password for logged-in user. | ✅ Yes |

## 📦 Listings (`/api/listings`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| Create Listing | `POST` | `/api/listings` | Adds a new item to the marketplace. | ✅ Yes |
| Get All Listings | `GET` | `/api/listings` | Retrieves all active listings (Deprecated in favor of Search). | ❌ No |
| Get Single Listing | `GET` | `/api/listings/{id}` | Fetches details for a specific item ID. | ❌ No |
| Update Listing | `PUT` | `/api/listings/{id}` | Edits title, price, description, etc. Owner only. | ✅ Yes |
| Delete Listing | `DELETE` | `/api/listings/{id}` | Removes a listing. Owner/Admin only. | ✅ Yes |
| Admin Delete | `DELETE` | `/api/listings/admin/{id}` | Force remove listing (Admin privilege). | ✅ Admin |
| Upload Image | `PUT` | `/api/listings/{id}/image` | Attaches/updates the main photo for a listing. | ✅ Yes |
| Buy Item | `POST` | `/api/listings/{id}/buy` | Executes a purchase transaction. Marks item as SOLD. | ✅ Yes |
| Search/Filter | `GET` | `/api/listings/search` | Unified endpoint for keyword search, category filtering, sorting, and pagination. Params: `keyword`, `categoryIds`, `sortBy`, `sortDir`, `page`, `size`. | ❌ No |

## 🏷️ Categories (`/api/categories`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| List Categories | `GET` | `/api/categories` | Returns all available tags/categories for filtering. | ❌ No |
| Get Category | `GET` | `/api/categories/{id}` | Fetches specific category details. | ❌ No |
| Create Category | `POST` | `/api/categories` | Adds a new global category tag. | ✅ Admin |
| Delete Category | `DELETE` | `/api/categories/{id}` | Removes a category tag. | ✅ Admin |

## 👤 Profile & Activity (`/api/profile`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| Get My Profile | `GET` | `/api/profile` | Fetches current user's profile data (Bio, Phone, Stats). | ✅ Yes |
| Update Profile | `PUT` | `/api/profile` | Saves changes to First Name, Last Name, Bio, Phone. | ✅ Yes |
| Upload Avatar | `PUT` | `/api/profile/image` | Updates the user's profile picture. | ✅ Yes |
| Get Activity Log | `GET` | `/api/profile/activity` | Returns chronological list of Audit Logs for the user. | ✅ Yes |

## 🔔 Real-Time Notifications (`/api/notifications`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| Subscribe Stream | `GET` | `/api/notifications/stream` | Opens an SSE connection for real-time events. Requires `?token=JWT`. | ✅ Yes |
| Test Send (Debug) | `POST` | `/api/notifications/test-send/{userId}` | Manually triggers a notification event for testing purposes. | ✅ Yes |