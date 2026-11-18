package com.example.marketplace.service;

import com.example.marketplace.client.AccountsClient;
import com.example.marketplace.client.AuditClient;
import com.example.marketplace.client.MediaClient;
import com.example.marketplace.client.MediaUploadResponse;
import com.example.marketplace.client.UserSummary;
import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.exceptions.ResourceNotFoundException;
import com.example.marketplace.messaging.DelistOutcome;
import com.example.marketplace.messaging.ListingEvent;
import com.example.marketplace.messaging.ListingLifecycle;
import com.example.marketplace.model.Categories;
import com.example.marketplace.model.Listings;
import com.example.marketplace.model.Transactions;
import com.example.marketplace.model.responses.MyListingResponse;
import com.example.marketplace.repository.CategoryRepository;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.repository.TransactionsRepository;
import com.example.marketplace.security.CurrentActor;
import com.example.marketplace.security.CurrentActorProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ListingService {

    private final AuditClient auditClient;
    private final ListingRepository listingsRepository;
    private final TransactionsRepository transactionsRepository;
    private final CurrentActorProvider currentActorProvider;
    private final AccountsClient accountsClient;
    private final MediaClient mediaClient;
    private final ListingLifecycle listingLifecycle;
    private final TransactionTemplate transactions;
    public final CategoryRepository categoryRepository;

    @Autowired
    public ListingService(
            ListingRepository listingsRepository,
            TransactionsRepository transactionsRepository,
            CurrentActorProvider currentActorProvider,
            AccountsClient accountsClient,
            MediaClient mediaClient,
            AuditClient auditClient,
            CategoryRepository categoryRepository,
            ListingLifecycle listingLifecycle,
            TransactionTemplate transactions
    ) {
        this.listingsRepository = listingsRepository;
        this.transactionsRepository = transactionsRepository;
        this.currentActorProvider = currentActorProvider;
        this.accountsClient = accountsClient;
        this.mediaClient = mediaClient;
        this.auditClient = auditClient;
        this.categoryRepository = categoryRepository;
        this.listingLifecycle = listingLifecycle;
        this.transactions = transactions;
    }

    // CREATE LISTING
    public Listings createListing(Listings listingRequest) {

        CurrentActor actor = currentActorProvider.get();

        if (actor.isAdmin()) {
            throw new InformationExistException("Admins cannot create listings");
        }

        Listings listing = new Listings();

        listing.setTitle(listingRequest.getTitle());
        listing.setDescription(listingRequest.getDescription());
        listing.setPrice(listingRequest.getPrice());
        listing.setCondition(listingRequest.getCondition());
        listing.setLocation(listingRequest.getLocation());

        listing.setStatus(Listings.Status.ACTIVE);
        listing.setUserId(actor.id());

        if (listingRequest.getCategories() != null && !listingRequest.getCategories().isEmpty()) {
            Set<Categories> categorySet = new HashSet<>();

            for (Categories cat : listingRequest.getCategories()) {
                // Fetch fresh from DB to ensure valid ID and avoid detached entity issues
                Categories dbCat = categoryRepository.findById(cat.getId()).orElse(null);
                if (dbCat != null) {
                    categorySet.add(dbCat);
                }
            }
            listing.setCategories(categorySet);
        } else {
            listing.setCategories(new HashSet<>());
        }

        auditClient.log(
                "LISTING_CREATED",
                actor.id(),
                actor.username(),
                "Created listing '" + listing.getTitle() + "' (id=" + listing.getId() + ")"
        );

        listingsRepository.save(listing);

        return listing;
    }

    // GET ONE LISTING
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Listings getListing(Long id) {

        Listings listing = listingsRepository.findById(id).orElseThrow(
                () -> new InformationExistException("no listing")
        );

        // Seller display data lives in Accounts; one lookup per detail view only.
        accountsClient.find(listing.getUserId())
                .ifPresent(user -> attachSeller(listing, user));

        return listing;
    }

    // GET ALL LISTINGS
    public List<Listings> getListings() {

        return listingsRepository.findAll();
    }

    // UPDATE LISTING
    @org.springframework.transaction.annotation.Transactional
    public Listings updateListing(
            Long id,
            Listings listingRequest
    ) {

        CurrentActor actor = currentActorProvider.get();

        if (actor.isAdmin()) {
            throw new InformationExistException(
                    "Admins cannot edit listings"
            );
        }

        Listings listing = listingsRepository
                .findByIdAndUserIdForUpdate(id, actor.id())
                .orElseThrow(() ->
                        new InformationExistException(
                                "Listing not found or you are not the owner"
                        ));

        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException(
                    "Only active listings can be edited"
            );
        }

        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(listing.getTitle(), listingRequest.getTitle())) {
            changedFields.add("title");
        }
        if (!Objects.equals(listing.getDescription(), listingRequest.getDescription())) {
            changedFields.add("description");
        }
        if (Double.compare(listing.getPrice(), listingRequest.getPrice()) != 0) {
            changedFields.add("price");
        }
        if (!Objects.equals(listing.getCondition(), listingRequest.getCondition())) {
            changedFields.add("condition");
        }
        if (!Objects.equals(listing.getLocation(), listingRequest.getLocation())) {
            changedFields.add("location");
        }

        listing.setTitle(listingRequest.getTitle());
        listing.setDescription(listingRequest.getDescription());
        listing.setPrice(listingRequest.getPrice());
        listing.setCondition(listingRequest.getCondition());
        listing.setLocation(listingRequest.getLocation());

        // Flushed here so @UpdateTimestamp is applied BEFORE the event is built:
        // the event must carry the timestamp the seller will actually see.
        listingsRepository.saveAndFlush(listing);
        listingLifecycle.updated(listing, changedFields);

        auditClient.log(
                "LISTING_UPDATED",
                actor.id(),
                actor.username(),
                "Updated listing '" + listing.getTitle() + "' (id=" + listing.getId() + ")"
        );

        return listing;
    }

    // DELETE LISTING
    @org.springframework.transaction.annotation.Transactional
    public void deleteListing(Long id) {

        CurrentActor actor = currentActorProvider.get();

        if (actor.isAdmin()) {
            throw new InformationExistException(
                    "Use the admin listing removal endpoint"
            );
        }

        Listings listing = listingsRepository
                .findByIdAndUserIdForUpdate(id, actor.id())
                .orElseThrow(() ->
                        new InformationExistException(
                                "Listing not found or you are not the owner"
                        ));

        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException(
                    "Only active listings can be removed"
            );
        }

        listing.setStatus(Listings.Status.DELISTED);
        listing.setStatusReason("Removed by you");

        listingsRepository.saveAndFlush(listing);

        // Favorites cleanup travels as one durable event, published after this
        // transaction commits. An owner cancellation sends no seller email.
        listingLifecycle.delisted(listing, ListingEvent.Source.OWNER, listing.getStatusReason());

        // AUDIT: listing delisted by owner
        auditClient.log(
                "LISTING_DELISTED",
                actor.id(),
                actor.username(),
                "Owner cancelled listing '" + listing.getTitle() + "' (id=" + id + ")"
        );
    }

    /** Kept for existing callers: default reason when no specific one applies. */
    @org.springframework.transaction.annotation.Transactional
    public void adminDeleteListing(Long id) {
        adminDeleteListing(id, "Removed by administrator");
    }

    @org.springframework.transaction.annotation.Transactional
    public void adminDeleteListing(Long id, String reason) {

        CurrentActor admin = currentActorProvider.get();

        if (!admin.isAdmin()) {
            throw new InformationExistException(
                    "Only admins can remove listings"
            );
        }

        // The browser admin endpoint keeps its old error behaviour; only the
        // internal/messaging path treats these as outcomes.
        DelistOutcome outcome = delist(id, reason, admin.id(), admin.username(),
                ListingEvent.Source.ADMIN);
        switch (outcome) {
            case NOT_FOUND -> throw new ResourceNotFoundException("Listing not found");
            case SOLD -> throw new InformationExistException("A sold listing cannot be removed");
            case ALREADY_DELISTED -> throw new InformationExistException(
                    "Listing has already been removed");
            case DELISTED, FAILED -> {
                // Nothing else to do: the delist already ran.
            }
        }
    }

    /**
     * Internal variant for trusted service calls that have already enforced the
     * admin role. The caller supplies the acting admin so the audit record stays
     * accurate even though the request carries the internal service token.
     *
     * <p>Returns an outcome instead of throwing for expected states, because the
     * RabbitMQ listener has to report what happened. Repeating the call is safe:
     * an already delisted listing returns ALREADY_DELISTED and repeats none of
     * the email, audit, or favorites work.</p>
     */
    @org.springframework.transaction.annotation.Transactional
    public DelistOutcome adminDeleteListingAs(Long id, String reason,
                                              Long adminId, String adminUsername) {

        if (adminId == null || adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin identity is required");
        }

        String effectiveReason = (reason == null || reason.isBlank())
                ? "Removed by moderation"
                : reason;

        return delist(id, effectiveReason, adminId, adminUsername,
                ListingEvent.Source.MODERATION);
    }

    private DelistOutcome delist(Long id, String reason, Long adminId, String adminUsername,
                                 ListingEvent.Source source) {

        Listings listing = listingsRepository.findByIdForUpdate(id).orElse(null);
        if (listing == null) {
            return DelistOutcome.NOT_FOUND;
        }

        // Admin cannot remove something that is already sold
        if (listing.getStatus() == Listings.Status.SOLD) {
            return DelistOutcome.SOLD;
        }
        if (listing.getStatus() == Listings.Status.DELISTED) {
            // Idempotency: a duplicate saga command must not email the seller or
            // write another audit entry.
            return DelistOutcome.ALREADY_DELISTED;
        }

        Long sellerId = listing.getUserId();
        String listingTitle = listing.getTitle();

        listing.setStatus(Listings.Status.DELISTED);
        listing.setStatusReason(reason);

        listingsRepository.saveAndFlush(listing);

        // Favorites cleanup AND the seller email travel as one durable event,
        // published after this transaction commits. A mail outage can no longer
        // roll the delist back, and a Favorites outage can no longer lose it.
        listingLifecycle.delisted(listing, source, reason);

        auditClient.log(
                "LISTING_DELISTED",
                adminId,
                adminUsername,
                "Admin removed listing '" + listingTitle + "' (id=" + id + ")",
                sellerId
        );

        return DelistOutcome.DELISTED;
    }

    // MY LISTINGS (profile page tab)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MyListingResponse> getMyListings() {
        CurrentActor me = currentActorProvider.get();

        List<Listings> listings = listingsRepository.findByUserIdOrderByCreatedAtDesc(me.id());

        // One query fetches the sales; buyer names are resolved through Accounts.
        List<Long> soldIds = listings.stream()
                .filter(l -> l.getStatus() == Listings.Status.SOLD)
                .map(Listings::getId)
                .toList();
        Map<Long, Transactions> salesByListing = soldIds.isEmpty()
                ? Map.of()
                : transactionsRepository.findByListingIdIn(soldIds).stream()
                    .collect(Collectors.toMap(t -> t.getListing().getId(), t -> t));

        Map<Long, String> buyerNames = new HashMap<>();

        return listings.stream()
                .map(listing -> {
                    Transactions sale = salesByListing.get(listing.getId());
                    String buyerName = sale == null ? null
                            : buyerNames.computeIfAbsent(sale.getBuyerId(), this::buyerDisplayName);
                    return MyListingResponse.from(listing, sale, buyerName);
                })
                .toList();
    }

    // UPLOAD / CHANGE LISTING IMAGE
    // The Media call runs outside a database transaction: holding this listing's
    // row lock for the whole upload would block unrelated updates. The URL update
    // and its after-commit lifecycle event still happen together in a transaction.
    public Listings uploadImage(
            Long id,
            MultipartFile img
    ) throws IOException {

        CurrentActor actor = currentActorProvider.get();

        // Fail before uploading when the caller does not own the listing, so the
        // common rejected case cannot leave an unreferenced asset.
        requireOwnedListing(id, actor.id());

        MediaUploadResponse uploaded = mediaClient.upload(actor.id(), img);

        return transactions.execute(status -> {
            Listings listing = requireOwnedListing(id, actor.id());
            // Store the relative path, never an absolute URL: the same row must
            // render on localhost, on the public IP and on the HTTPS domain.
            boolean imageChanged = !Objects.equals(listing.getImgUrl(), uploaded.path());
            listing.setImgUrl(uploaded.path());
            listing.setImg(null);
            listing.setImgtype(null);

            listingsRepository.saveAndFlush(listing);
            if (imageChanged) {
                listingLifecycle.updated(listing, List.of("image"));
            }
            return listing;
        });
    }

    private Listings requireOwnedListing(Long id, Long ownerId) {
        return listingsRepository
                .findByIdAndUserId(id, ownerId)
                .orElseThrow(() -> new InformationExistException(
                        "Listing not found or you are not the owner"));
    }

    public Page<Listings> searchListings(
            String keyword,
            List<Long> categoryIds,
            String sortBy,
            String sortDir,
            int page,
            int size
    ) {
        String cleanKeyword = (keyword != null) ? keyword.trim() : "";
        boolean hasCategories = (categoryIds != null && !categoryIds.isEmpty());

        if (!hasCategories) {
            categoryIds = new ArrayList<>();
        }

        // Determine Sort Direction
        String cleanSortDir = (sortDir != null && sortDir.equalsIgnoreCase("desc")) ? "desc" : "asc";
        Sort.Direction direction = cleanSortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;

        // Build Sort Object
        Sort sort;
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            if (sortBy.equalsIgnoreCase("price")) {
                sort = Sort.by(direction, "price").and(Sort.by(direction, "condition"));
            } else if (sortBy.equalsIgnoreCase("condition")) {
                sort = Sort.by(direction, "condition").and(Sort.by(direction, "price"));
            } else {
                sort = Sort.by(direction, "price");
            }
        } else {
            // Default sort by newest first (createdAt desc) is usually better for marketplaces
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        // Create Pageable object
        Pageable pageable = PageRequest.of(page, size, sort);

        // Execute Query
        return listingsRepository.searchListingsPaginated(
                Listings.Status.ACTIVE,
                cleanKeyword,
                hasCategories,
                categoryIds,
                hasCategories ? categoryIds.size() : 0,
                pageable
        );

    }

    private void attachSeller(Listings listing, UserSummary user) {
        Map<String, Object> seller = new HashMap<>();
        seller.put("id", user.id());
        seller.put("username", user.username());
        seller.put("email", user.email());
        // Transient view of Accounts data; the JSON shape stays {id, username, email}.
        listing.setSeller(seller);
    }

    private String buyerDisplayName(Long buyerId) {
        return accountsClient.find(buyerId)
                .map(user -> user.username() != null ? user.username() : user.email())
                .orElse(null);
    }
}
