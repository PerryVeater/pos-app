package com.example.posapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entity class representing a physical store owned by an {@link Organization}.
 * <p>
 * Each store belongs to exactly one organization; the {@code organization}
 * association is required and enforced by the {@code fk_stores_organization}
 * foreign key at the database level. A store carries its own identity fields
 * (an internal {@code id}, an optional human/business {@code storeNumber}
 * that is unique within its organization, and a required non-unique
 * {@code name}), a primary physical address, optional contact details, and a
 * required IANA {@code timezone}.
 * </p>
 * <p>
 * There is no separate Address entity yet; the tenancy foundation models the
 * primary address as columns on this table.
 * </p>
 */
@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning organization. Required: a store cannot exist without one, so
     * the column is NOT NULL and the join column references
     * {@code organization(id)} through {@code fk_stores_organization}.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_stores_organization"))
    private Organization organization;

    /**
     * Optional human/business identifier unique within the owning
     * organization when present. Enforced by the composite
     * {@code uk_stores_organization_number} constraint; PostgreSQL treats
     * NULLs as distinct so multiple stores in an organization may leave this
     * unset.
     */
    @Column(name = "store_number", length = 64)
    private String storeNumber;

    /**
     * Required human-readable store name. Not globally unique; two stores
     * may share the same name within or across organizations. Use
     * {@code storeNumber} for a stable business identifier.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Lifecycle state: whether the store is currently in service. Inactive
     * stores are kept rather than deleted, and new stores default to active.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * ISO 3166-1 alpha-2 country code (e.g. {@code "US"}, {@code "MX"}).
     * Required and stored uppercase, two characters wide.
     */
    @Column(nullable = false, length = 2)
    private String country;

    /**
     * Required state or province component of the primary address.
     */
    @Column(name = "state_province", nullable = false, length = 80)
    private String stateProvince;

    /**
     * Required city component of the primary address.
     */
    @Column(nullable = false, length = 80)
    private String city;

    /**
     * Required first line of the primary street address.
     */
    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    /**
     * Optional second line of the primary street address (suite, unit, etc.).
     */
    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    /**
     * Required postal code. Stored as a string because many countries use
     * non-numeric postal codes.
     */
    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    /**
     * Optional phone number. Stored as a string to preserve leading zeros,
     * punctuation, and international dial prefixes.
     */
    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    /**
     * Optional contact email address. Validated at the API boundary when
     * provided.
     */
    @Column(length = 255)
    private String email;

    /**
     * Required IANA time zone identifier (e.g. {@code "America/Los_Angeles"},
     * {@code "America/Mexico_City"}). A UTC offset is not stored because it
     * cannot represent daylight-saving transitions.
     */
    @Column(nullable = false, length = 64)
    private String timezone;

    /**
     * Default constructor for Store.
     */
    public Store() {}

    /**
     * Full constructor capturing the required fields for a persisted store.
     * Optional fields ({@code storeNumber}, {@code addressLine2},
     * {@code phoneNumber}, {@code email}) are set through their setters.
     * @param organization the owning organization
     * @param name the store name
     * @param country the ISO country code
     * @param stateProvince the state or province
     * @param city the city
     * @param addressLine1 the primary street address
     * @param postalCode the postal code
     * @param timezone the IANA time zone identifier
     */
    public Store(Organization organization,
                 String name,
                 String country,
                 String stateProvince,
                 String city,
                 String addressLine1,
                 String postalCode,
                 String timezone) {
        this.organization = organization;
        this.name = name;
        this.country = country;
        this.stateProvince = stateProvince;
        this.city = city;
        this.addressLine1 = addressLine1;
        this.postalCode = postalCode;
        this.timezone = timezone;
    }

    /**
     * Get the ID of the store.
     * @return the ID of the store
     */
    public Long getId() { return id; }

    /**
     * Get the owning organization.
     * @return the organization that owns this store
     */
    public Organization getOrganization() { return organization; }

    /**
     * Set the owning organization.
     * @param organization the organization that owns this store
     */
    public void setOrganization(Organization organization) { this.organization = organization; }

    /**
     * Get the optional store number.
     * @return the human/business identifier, or {@code null} when unset
     */
    public String getStoreNumber() { return storeNumber; }

    /**
     * Set the optional store number.
     * @param storeNumber the human/business identifier
     */
    public void setStoreNumber(String storeNumber) { this.storeNumber = storeNumber; }

    /**
     * Get the name of the store.
     * @return the name of the store
     */
    public String getName() { return name; }

    /**
     * Set the name of the store.
     * @param name the name of the store
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get whether the store is in service.
     * @return {@code true} if the store is currently active
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the store is in service.
     * @param active whether the store is currently active
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Get the ISO country code.
     * @return the two-letter country code
     */
    public String getCountry() { return country; }

    /**
     * Set the ISO country code.
     * @param country the two-letter country code
     */
    public void setCountry(String country) { this.country = country; }

    /**
     * Get the state or province.
     * @return the state or province component of the address
     */
    public String getStateProvince() { return stateProvince; }

    /**
     * Set the state or province.
     * @param stateProvince the state or province component of the address
     */
    public void setStateProvince(String stateProvince) { this.stateProvince = stateProvince; }

    /**
     * Get the city.
     * @return the city component of the address
     */
    public String getCity() { return city; }

    /**
     * Set the city.
     * @param city the city component of the address
     */
    public void setCity(String city) { this.city = city; }

    /**
     * Get the primary street address line.
     * @return the first address line
     */
    public String getAddressLine1() { return addressLine1; }

    /**
     * Set the primary street address line.
     * @param addressLine1 the first address line
     */
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    /**
     * Get the optional secondary street address line.
     * @return the second address line, or {@code null} when unset
     */
    public String getAddressLine2() { return addressLine2; }

    /**
     * Set the optional secondary street address line.
     * @param addressLine2 the second address line
     */
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    /**
     * Get the postal code.
     * @return the postal code
     */
    public String getPostalCode() { return postalCode; }

    /**
     * Set the postal code.
     * @param postalCode the postal code
     */
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    /**
     * Get the phone number.
     * @return the phone number, or {@code null} when unset
     */
    public String getPhoneNumber() { return phoneNumber; }

    /**
     * Set the phone number.
     * @param phoneNumber the phone number
     */
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    /**
     * Get the contact email.
     * @return the email, or {@code null} when unset
     */
    public String getEmail() { return email; }

    /**
     * Set the contact email.
     * @param email the email
     */
    public void setEmail(String email) { this.email = email; }

    /**
     * Get the IANA time zone identifier.
     * @return the store's time zone
     */
    public String getTimezone() { return timezone; }

    /**
     * Set the IANA time zone identifier.
     * @param timezone the store's time zone
     */
    public void setTimezone(String timezone) { this.timezone = timezone; }

    /**
     * Return a string representation of the store.
     * @return a string representation of the store
     */
    @Override
    public String toString() {
        return "Store{id=" + id
                + ", organization=" + (organization == null ? null : organization.getId())
                + ", storeNumber='" + storeNumber + "'"
                + ", name='" + name + "'"
                + ", active=" + active
                + ", country='" + country + "'"
                + ", stateProvince='" + stateProvince + "'"
                + ", city='" + city + "'"
                + ", timezone='" + timezone + "'}";
    }
}
