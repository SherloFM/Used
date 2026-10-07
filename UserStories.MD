# User Stories: Used Marketplace

## Epic: Authentication & Security
- **As a new user**, I want to register with my username, email, and password so that I can create an account.
- **As a new user**, I want to verify my email address via a 6-digit code so that my account is activated and trusted by others.
- **As a registered user**, I want to log in securely using JWT tokens so that my session persists across pages without re-entering credentials.
- **As a forgot-password user**, I want to reset my password via email verification so that I can regain access to my account if I forget my credentials.
- **As a guest**, I want to be redirected to the login page when trying to access protected features (like selling or buying) so that the marketplace remains secure.

## Epic: Browsing & Discovery
- **As a shopper**, I want to search for items by keyword (e.g., "iPhone", "Sofa") so that I can find specific products quickly.
- **As a shopper**, I want to filter results by multiple categories (Tags) simultaneously so that I can narrow down niche interests (e.g., Electronics + Gaming).
- **As a shopper**, I want to see paginated results (12 items per page) so that the interface remains fast and responsive even with thousands of listings.
- **As a shopper**, I want to view detailed information about a listing (price, condition, location, seller info) before making a decision.

## Epic: Selling & Listing Management
- **As a seller**, I want to create a new listing with title, description, price, condition, and photos so that I can sell my unused items.
- **As a seller**, I want to tag my listing with relevant categories so that buyers can find it through search filters.
- **As a seller**, I want to upload high-quality images for my product so that potential buyers can see exactly what they are purchasing.
- **As a seller**, I want to edit or delete my active listings if prices change or items are sold elsewhere.

## Epic: Transactions & Notifications
- **As a buyer**, I want to click "Buy Now" on a listing to initiate a purchase transaction.
- **As a seller**, I want to receive real-time notifications (via SSE) when someone views or interacts with my listing.
- **As a system admin/user**, I want to see an audit trail of all actions (login, create listing, buy item) in my profile activity feed so that I have full transparency over my account history.
- **As a user**, I want to update my profile picture and bio so that I can personalize my presence on the marketplace.

## Epic: Profile & Settings
- **As a user**, I want to view my personal statistics (Active Listings, Items Sold) on my dashboard.
- **As a user**, I want to see my recent activity log (registrations, purchases, edits) chronologically.
- **As a user**, I want to logout securely which clears my session token from local storage.