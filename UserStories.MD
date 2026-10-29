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
- **As a user**, I want persisted notifications and live SSE updates for supported listing and payment events so that I can follow their outcomes.
- **As a system admin/user**, I want to see an audit trail of all actions (login, create listing, buy item) in my profile activity feed so that I have full transparency over my account history.
- **As a user**, I want to update my profile picture and bio so that I can personalize my presence on the marketplace.

## Epic: Profile & Settings
- **As a user**, I want to view my personal statistics (Active Listings, Items Sold) on my dashboard.
- **As a user**, I want to see my recent activity log (registrations, purchases, edits) chronologically.
- **As a user**, I want to logout securely which clears my session token from local storage.

## Epic: Favorites
- **As a shopper**, I want a star that fills only after a successful save, and empties after removal, so that the UI reflects my saved state.
  - Acceptance: state reloads from the backend; errors do not falsely toggle the star; keyboard users have a named toggle with pressed state.
- **As a shopper**, I want a responsive My Favorites grid with title, price, availability, View and Remove actions.
  - Acceptance: twelve entries per page, working previous/next controls, and a helpful empty state.
- **As a shopper**, I want saved listings removed when sold/delisted, with an in-app notification and email explaining why.
  - Acceptance: rollback sends no unavailable email; delivery failure does not reverse a sale. Saving a listing while it is being purchased is rejected or cleaned up, never left behind.
- **As a shopper**, I want notifications when a saved listing changes so I can reconsider it.

## Epic: My Listings overview
- **As a seller**, I want a "My Listings" tab on my profile showing every listing I own with its current status (Active, Sold, Delisted).
  - Acceptance: sold rows show who bought them, delisted rows show why (by me, by an admin, or by moderation), ACTIVE rows show neither; counts feed the profile stats; no pagination needed at profile scale.

## Epic: Reporting and moderation
- **As a member**, I want to report a listing with a reason and optional details.
  - Acceptance: own listings are rejected; duplicate open reports are blocked; descriptions are limited to 1000 characters; submission rate is limited.
- **As an administrator**, I want a filtered, paginated report queue with listing history and internal notes.
- **As an administrator**, I want to mark reports under review, dismiss them, or take action and optionally delist the item.
  - Acceptance: only pending reports move to review; resolved reports cannot be resolved again; sold-listing refusal is recorded in the resolution notes.
- **As an administrator**, I want consistent dashboard, users, listings and reports screens, and admin login should take me to the portal.
  - Acceptance: non-admin users are redirected home; backend admin APIs enforce the role independently of the page guard.

## Epic: Reliable payments and notifications
- **As a buyer**, I want to see payment processing separately from completion.
  - Acceptance: HTTP 202 is acceptance only; the UI polls until a terminal result.
- **As competing buyers**, we want only one completed purchase for the same listing.
  - Acceptance: exactly one completed transaction, one seller credit, and no charge to the losing buyer.
- **As a wallet owner**, I want simultaneous purchases to respect my available balance.
  - Acceptance: conditional debits cannot overdraw; failed purchases roll back listing and notification changes.
- **As a seller**, I want to accept or decline queued COD requests; unanswered requests expire.
- **As a user**, I want notifications to remain available after reconnecting.
  - Acceptance: persisted history and read state remain available; SSE publishes after commit and clients deduplicate replayed notification IDs.
+- **As a chat participant**, I want to delete a conversation from my own chat list without affecting the other person.
+  - Acceptance: the chat disappears from my list only; sending a new message brings it back for the recipient so no message is ever lost.

## Epic: Consistent and accessible navigation
- **As a keyboard user**, I want visible focus and descriptive icon-button labels.
- **As a mobile user**, I want cards, navigation, forms and admin actions to remain usable without clipped content.
- **As a motion-sensitive user**, I want reduced-motion settings respected.
- **As a user**, I want consistent primary, destructive, success and favorite colors across account, marketplace and admin pages.
