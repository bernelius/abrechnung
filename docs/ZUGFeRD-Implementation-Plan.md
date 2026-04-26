# ZUGFeRD Implementation Plan

**Version:** 1.0  
**Date:** 2026-04-26  
**Status:** Draft  
**Branch:** `zugferd`

---

## 1. Overview

This document outlines the implementation of ZUGFeRD 2.2 (Factur-X) support for the abrechnung application. ZUGFeRD is the European standard for electronic invoicing, mandated for B2G transactions and increasingly adopted for B2B.

### 1.1 Goals

- Generate ZUGFeRD-compliant XML invoices
- Maintain existing PDF design (Inter font, custom layout)
- Phase 1: Separate PDF + XML files
- Phase 2: True hybrid PDF/A-3 with embedded XML

### 1.2 Approach Summary

We use **Option C**: Start with separate files, add PDF embedding as Phase 2.

1. **Phase 1**: Keep OpenPDF for beautiful PDFs, add MustangProject for XML generation
2. **Phase 2**: Use Apache PDFBox to embed XML into PDF/A-3

---

## 2. Current State Analysis

### 2.1 Existing Data Model

```
Invoice: id, invoiceDate, dueDate, vatRate, currency, status, recipientId
InvoiceItem: description, quantity, unitPrice, discount
Recipient: companyName, address (free text), postal (free text), email, orgNumber
Seller (UserConfig): name, address (free text), postal (free text), email, accountNumber, orgNumber
```

### 2.2 What's Missing

| Category | Current State | Required for ZUGFeRD |
|----------|---------------|---------------------|
| Address format | Free text | Structured (street, building, city, postal, country) |
| Country | Missing | ISO 3166-1 alpha-2 mandatory |
| VAT ID | orgNumber (optional) | vatId (mandatory for seller) |
| Invoice type | Implicit | BT-3 invoice type code (380=Commercial) |
| Payment info | accountNumber (free text) | IBAN, BIC, payment means code |
| VAT breakdown | Single rate | Per-category breakdown |
| Line items | Basic | Line IDs, unit codes, net prices |

---

## 3. Phase 1: Separate Files (PDF + XML)

### 3.1 Database Schema Changes

#### 3.1.1 RecipientsTable (Buyers)

```kotlin
object RecipientsTable : Table() {
    // Existing fields (keep for backward compatibility)
    val id = integer("id").autoIncrement()
    val companyName = varchar("company_name", length = 255).uniqueIndex()
    val email = varchar("email", length = 255)
    val orgNumber = varchar("org_number", length = 20).nullable().uniqueIndex()
    
    // DEPRECATED: Free-text address fields (nullable, for migration)
    val address = varchar("address", length = 255).nullable()  // Legacy
    val postal = varchar("postal", length = 255).nullable()    // Legacy
    
    // NEW: Structured address fields
    val street = varchar("street", length = 255)           // Street name
    val buildingNumber = varchar("building_number", 20).nullable()  // House/building number
    val city = varchar("city", 100)                        // City name
    val postalCode = varchar("postal_code", 20)            // Postal/ZIP code
    val countryCode = varchar("country_code", 2)           // ISO 3166-1 alpha-2 (e.g., "NO", "DE")
    
    // NEW: Identifiers
    val vatId = varchar("vat_id", 30).nullable()           // VAT identification number
    
    // Existing
    var keybind = char("keybind").nullable()
    
    override val primaryKey = PrimaryKey(id)
}
```

#### 3.1.2 UserConfigTable (Seller)

```kotlin
object UserConfigTable : Table(name = "user_config") {
    val id = integer("id").default(1)
    val name = varchar("name", length = 50)                // Legal name
    val tradingName = varchar("trading_name", 255).nullable()  // Trading name (if different)
    val email = varchar("email", length = 255)
    
    // DEPRECATED: Free-text fields (nullable, for migration)
    val address = varchar("address", length = 80).nullable()   // Legacy
    val postal = varchar("postal", length = 80).nullable()     // Legacy
    
    // NEW: Structured address fields
    val street = varchar("street", 255)                    // Street name
    val buildingNumber = varchar("building_number", 20).nullable()
    val city = varchar("city", 100)
    val postalCode = varchar("postal_code", 20)
    val countryCode = varchar("country_code", 2)          // Mandatory
    
    // NEW: Identifiers
    val vatId = varchar("vat_id", 30)                      // Mandatory for ZUGFeRD
    val orgNumber = varchar("org_number", 20).nullable()
    
    // DEPRECATED: Generic account number (migrate to IBAN)
    internal val encryptedAccountNumber = varchar("account_number", length = 255)
    
    // NEW: Structured payment info
    val paymentAccountIban = varchar("payment_iban", 34).nullable()
    val paymentAccountBic = varchar("payment_bic", 11).nullable()
    
    // SMTP config (existing)
    val smtpHost = varchar("smtp_host", length = 255).nullable()
    val smtpPort = varchar("smtp_port", length = 5).nullable()
    val smtpUser = varchar("smtp_user", length = 255).nullable()
    internal val encryptedEmailPassword = varchar("email_password", length = 255).nullable()
    
    // Delegates (existing)
    var emailPassword by OptionalEncryptedDelegate(encryptedEmailPassword)
    var accountNumber by RequiredEncryptedDelegate(encryptedAccountNumber)
    
    override val primaryKey = PrimaryKey(id)
}
```

#### 3.1.3 InvoicesTable

```kotlin
object InvoicesTable : Table() {
    val id = integer("id").autoIncrement()
    val invoiceDate = date("invoice_date")
    val dueDate = date("due_date")
    
    // NEW: Tax point date (when VAT becomes accountable)
    val taxPointDate = date("tax_point_date").nullable()
    
    // Existing (modify to support per-line VAT)
    val vatRate = integer("vat_rate").default(0)  // Default rate (fallback)
    val currency = varchar("currency", length = 10)
    val status = varchar("status", length = 20).default("pending")
    val recipientId = integer("recipient_id").references(RecipientsTable.id)
    
    // NEW: Invoice metadata
    val invoiceTypeCode = varchar("invoice_type_code", 3).default("380")  // 380=Commercial
    
    // NEW: References
    val buyerReference = varchar("buyer_reference", 255).nullable()          // BT-10 (XRechnung mandatory)
    val purchaseOrderReference = varchar("po_reference", 255).nullable()     // BT-13
    val salesOrderReference = varchar("so_reference", 255).nullable()        // BT-14
    val precedingInvoiceNumber = varchar("preceding_invoice_number", 255).nullable()  // BT-25
    val precedingInvoiceDate = date("preceding_invoice_date").nullable()     // BT-26
    
    // NEW: Payment information
    val paymentMeansCode = varchar("payment_means_code", 3).default("30")    // 30=Bank transfer
    val paymentAccountIban = varchar("payment_iban", 34).nullable()
    val paymentAccountBic = varchar("payment_bic", 11).nullable()
    val paymentTerms = varchar("payment_terms", 500).nullable()
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        check { status inList listOf("pending", "paid", "failed", "invalid") }
    }
}
```

#### 3.1.4 New Table: InvoiceVatBreakdownTable

VAT must be broken down by category (Standard, Zero-rated, Exempt, etc.):

```kotlin
object InvoiceVatBreakdownTable : Table(name = "invoice_vat_breakdown") {
    val id = integer("id").autoIncrement()
    val invoiceId = integer("invoice_id").references(InvoicesTable.id)
    
    // VAT category: S=Standard, Z=Zero, E=Exempt, G=Free export, O=Services outside scope, K=VAT exempt for EEA
    val vatCategoryCode = varchar("vat_category_code", 1)
    
    val vatRate = integer("vat_rate")                      // Percentage (0 for Z, E)
    val taxableAmount = decimal("taxable_amount", 15, 2)   // 2 decimal places for totals
    val taxAmount = decimal("tax_amount", 15, 2)
    
    // For exempt/zero-rated: reason and code from VATEX codelist
    val vatExemptionReason = varchar("exemption_reason", 255).nullable()
    val vatExemptionReasonCode = varchar("exemption_code", 10).nullable()  // VATEX code
    
    override val primaryKey = PrimaryKey(id)
}
```

#### 3.1.5 InvoiceItemsTable

```kotlin
object InvoiceItemsTable : Table(name = "invoice_items") {
    val id = integer("id").autoIncrement()
    
    // NEW: Sequential line number (1, 2, 3...)
    val lineId = integer("line_id")
    
    val description = varchar("description", length = 255)
    
    // Change to Double for fractional quantities
    val quantity = decimal("quantity", 12, 4)              // 4 decimal places for quantities
    
    // NEW: Unit code (UN/ECE Recommendation 20)
    val unitCode = varchar("unit_code", 3).default("C62")  // C62=unit, KGM=kg, LTR=liter
    
    // Prices
    val unitPrice = decimal("unit_price", 15, 4)          // 4 decimal places for prices
    val netPrice = decimal("net_price", 15, 4)            // Price after item-level discounts
    
    // Existing
    val discount = integer("discount").default(0)
    
    // NEW: Line net amount (quantity * netPrice)
    val lineNetAmount = decimal("line_net_amount", 15, 2)
    
    // NEW: Per-line VAT (can differ from document default)
    val vatRate = integer("vat_rate").default(0)
    val vatCategoryCode = varchar("line_vat_category", 1).default("S")
    
    // NEW: Item identifiers
    val sellerAssignedId = varchar("seller_item_id", 50).nullable()
    val buyerAssignedId = varchar("buyer_item_id", 50).nullable()
    val standardIdentifier = varchar("standard_identifier", 35).nullable()  // GTIN/EAN
    
    // NEW: Billing period
    val periodStartDate = date("period_start").nullable()
    val periodEndDate = date("period_end").nullable()
    
    val invoiceId = integer("invoice_id").references(InvoicesTable.id)
    
    override val primaryKey = PrimaryKey(id)
}
```

---

### 3.2 DTO Model Updates

#### 3.2.1 RecipientDTO (Buyer)

```kotlin
data class RecipientDTO(
    var id: Int = 0,
    var companyName: String = "",
    var email: String = "",
    
    // Structured address (NEW - source of truth)
    var street: String = "",
    var buildingNumber: String? = null,
    var city: String = "",
    var postalCode: String = "",
    var countryCode: String = "",  // ISO 3166-1 alpha-2
    
    // LEGACY: Free-text address (for backward compatibility)
    var address: String? = null,
    var postal: String? = null,
    
    // Identifiers
    var orgNumber: String? = null,      // Organization number (e.g., Norwegian org nr)
    var vatId: String? = null,          // VAT ID (e.g., NO123456789MVA)
    
    var keybind: Char? = null,
) {
    companion object {
        val companyNameValidators = arrayOf(isNotBlank)
        val emailValidators = arrayOf(isNotBlank, isEmail)
        val streetValidators = arrayOf(isNotBlank)
        val cityValidators = arrayOf(isNotBlank)
        val postalCodeValidators = arrayOf(isNotBlank)
        val countryCodeValidators = arrayOf(isValidCountryCode)  // Must be ISO 3166-1 alpha-2
    }
}
```

#### 3.2.2 UserConfigDTO (Seller)

```kotlin
data class UserConfigDTO(
    var name: String = "",                  // Legal name
    var tradingName: String? = null,        // Trading name (if different from legal)
    var email: String = "",
    
    // Structured address (NEW)
    var street: String = "",
    var buildingNumber: String? = null,
    var city: String = "",
    var postalCode: String = "",
    var countryCode: String = "",          // Mandatory for ZUGFeRD
    
    // LEGACY: Free-text (for backward compatibility)
    var address: String? = null,
    var postal: String? = null,
    
    // Identifiers
    var vatId: String = "",                 // Mandatory for ZUGFeRD
    var orgNumber: String? = null,
    
    // Payment information (structured)
    var accountNumber: String = "",         // Keep as IBAN (backward compat)
    var accountBic: String? = null,         // BIC/SWIFT if applicable
    
    // SMTP config
    var smtpHost: String? = null,
    var smtpPort: String? = null,
    var smtpUser: String? = null,
    var emailPassword: String? = null,
) {
    companion object {
        val nameValidators = arrayOf(isNotBlank)
        val emailValidators = arrayOf(isEmail)
        val streetValidators = arrayOf(isNotBlank)
        val cityValidators = arrayOf(isNotBlank)
        val postalCodeValidators = arrayOf(isNotBlank)
        val countryCodeValidators = arrayOf(isValidCountryCode)
        val vatIdValidators = arrayOf(isValidVatId)  // Format validation
        val accountNumberValidators = arrayOf(isValidIban)  // IBAN format
    }
    
    fun isValid(): Boolean = 
        name.isNotBlank() &&
        email.contains("@") &&
        street.isNotBlank() &&
        city.isNotBlank() &&
        postalCode.isNotBlank() &&
        countryCode.isNotBlank() &&
        vatId.isNotBlank()
}
```

#### 3.2.3 InvoiceDTO

```kotlin
data class InvoiceDTO(
    var id: Int,
    var invoiceDate: LocalDate,
    var dueDate: LocalDate,
    var taxPointDate: LocalDate?,         // When VAT becomes accountable (nullable)
    
    // Document metadata
    var invoiceTypeCode: String = "380",  // 380=Commercial invoice, 381=Credit note
    var currency: String,
    var status: String,
    
    // References
    var buyerReference: String?,          // BT-10 (buyer's internal ref)
    var purchaseOrderReference: String?,  // BT-13
    var salesOrderReference: String?,     // BT-14
    var precedingInvoiceNumber: String?,  // BT-25 (for corrections/credits)
    var precedingInvoiceDate: LocalDate?, // BT-26
    
    // Payment
    var paymentMeansCode: String = "30",  // 30=Bank transfer
    var paymentAccountIban: String?,
    var paymentAccountBic: String?,
    var paymentTerms: String?,
    
    // VAT breakdown by category
    var vatBreakdown: List<VatBreakdownDTO>,
    
    // Line items
    var invoiceItems: List<InvoiceItemDTO>,
    
    // Recipient (buyer)
    var recipient: RecipientDTO,
) {
    // Calculated fields
    val subtotal: Double
        get() = invoiceItems.sumOf { it.lineNetAmount }
    
    val vatAmount: Double
        get() = vatBreakdown.sumOf { it.taxAmount }
    
    val total: Double
        get() = subtotal + vatAmount
}
```

#### 3.2.4 VatBreakdownDTO (NEW)

```kotlin
data class VatBreakdownDTO(
    val vatCategoryCode: String,          // S=Standard, Z=Zero, E=Exempt, G=Free export, O=Services outside scope, K=Exempt for EEA
    val vatRate: Int,                     // Percentage (0 for Z, E, etc.)
    val taxableAmount: Double,
    val taxAmount: Double,
    val exemptionReason: String? = null,  // Required for Z, E categories
    val exemptionReasonCode: String? = null,  // VATEX code (e.g., "VATEX-EU-79-C")
)

// VAT Category Codes (EN16931)
object VatCategoryCodes {
    const val STANDARD = "S"              // Standard rate
    const val ZERO = "Z"                  // Zero-rated (exports, etc.)
    const val EXEMPT = "E"                // VAT exempt
    const val FREE_EXPORT = "G"           // Free export item, tax not charged
    const val OUTSIDE_SCOPE = "O"         // Services outside scope of tax
    const val EXEMPT_EEA = "K"            // VAT exempt for EEA intra-community supply
}
```

#### 3.2.5 InvoiceItemDTO

```kotlin
data class InvoiceItemDTO(
    val id: Int = 0,
    val lineId: Int,                      // Sequential line number (1, 2, 3...)
    
    var description: String = "",
    var quantity: Double = 0.0,           // Changed from Int to Double
    var unitCode: String = "C62",         // UN/ECE Rec 20 (C62=unit, KGM=kg, etc.)
    
    // Prices (4 decimal places internally, 2 for display)
    var unitPrice: Double = 0.0,          // Gross price
    var netPrice: Double = 0.0,           // Price after line-level discount
    
    // Discount
    var discount: Int = 0,
    
    // Calculated line amount
    var lineNetAmount: Double = 0.0,      // quantity * netPrice
    
    // VAT for this line (can differ from document default)
    var vatRate: Int = 0,
    var vatCategoryCode: String = "S",
    
    // Item identifiers
    var sellerAssignedId: String? = null,
    var buyerAssignedId: String? = null,
    var standardIdentifier: String? = null,  // GTIN/EAN
    
    // Billing period (optional)
    var periodStartDate: LocalDate? = null,
    var periodEndDate: LocalDate? = null,
) {
    companion object {
        val descriptionValidators = arrayOf(isNotBlank)
        val quantityValidators = arrayOf(isPositiveDouble)
        val unitCodeValidators = arrayOf(isValidUnitCode)  // UN/ECE Rec 20
        val unitPriceValidators = arrayOf(isPositiveDouble)
        val vatRateValidators = arrayOf(isValidVatRate)
    }
}
```

---

### 3.3 ZUGFeRD Module Structure

```
app/src/main/kotlin/com/bernelius/abrechnung/
├── zugferd/
│   ├── ZugferdGenerator.kt          # Main entry point for XML generation
│   ├── ZugferdMapper.kt             # Maps DTOs to ZUGFeRD XML structure
│   ├── ZugferdConfig.kt             # Configuration (profile, version)
│   ├── model/
│   │   ├── ZugferdInvoice.kt        # Internal representation
│   │   ├── ZugferdParty.kt          # Seller/Buyer model
│   │   ├── ZugferdLineItem.kt       # Line item model
│   │   └── ZugferdVatBreakdown.kt   # VAT breakdown model
│   ├── validation/
│   │   └── ZugferdValidator.kt      # Validation rules
│   └── constants/
│       ├── VatCategoryCodes.kt      # S, Z, E, G, O, K
│       ├── UnitCodes.kt             # UN/ECE Rec 20 codes
│       ├── PaymentMeansCodes.kt     # 30, 48, 49, etc.
│       └── InvoiceTypeCodes.kt      # 380, 381, etc.
```

---

### 3.4 Dependencies

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    // Existing dependencies...
    
    // ZUGFeRD / MustangProject
    implementation("org.mustangproject:library:2.14.0")
    
    // PDFBox for Phase 2 (PDF/A-3 embedding)
    implementation("org.apache.pdfbox:pdfbox:3.0.0")
    implementation("org.apache.pdfbox:xmpbox:3.0.0")  // XMP metadata
    
    // Validation (optional, for testing)
    testImplementation("org.mustangproject:validator:2.14.0")
}
```

---

### 3.5 ZUGFeRD Generator Implementation

#### 3.5.1 Configuration

```kotlin
// ZugferdConfig.kt
object ZugferdConfig {
    const val VERSION = "2.2"                    // ZUGFeRD version
    const val PROFILE = "EN16931"                // Minimum, BASIC, EN16931, EXTENDED
    const val XML_FILENAME = "factur-x.xml"      // Embedded filename
    const val CONFORMANCE_LEVEL = "EN 16931"     // XMP metadata
    
    // EAS Codes (Electronic Address Scheme)
    const val EAS_EMAIL = "9920"                 // Electronic mail (SMTP)
}
```

#### 3.5.2 Generator Interface

```kotlin
// ZugferdGenerator.kt
interface ZugferdGenerator {
    /**
     * Generate ZUGFeRD XML from invoice data
     * @return XML as byte array (UTF-8)
     */
    fun generateXml(invoice: InvoiceDTO, seller: UserConfigDTO): ByteArray
    
    /**
     * Generate and save XML to file
     */
    fun generateXmlToFile(invoice: InvoiceDTO, seller: UserConfigDTO, outputPath: String)
    
    /**
     * Validate generated XML against ZUGFeRD schema
     */
    fun validateXml(xmlBytes: ByteArray): ValidationResult
}

class MustangZugferdGenerator : ZugferdGenerator {
    override fun generateXml(invoice: InvoiceDTO, seller: UserConfigDTO): ByteArray {
        // Use MustangProject to generate XML
        val mustangInvoice = mapToMustangInvoice(invoice, seller)
        val exporter = ZUGFeRDExporterFromA1()
        exporter.setInvoice(mustangInvoice)
        exporter.setProfile(ZugferdConfig.PROFILE)
        return exporter.getXML()
    }
    
    // ... implementation
}
```

#### 3.5.3 Mapper Implementation (Key Mappings)

```kotlin
// ZugferdMapper.kt - Key mapping examples

object ZugferdMapper {
    
    fun mapToMustangInvoice(invoice: InvoiceDTO, seller: UserConfigDTO): Invoice {
        val mustangInvoice = Invoice()
        
        // Header
        mustangInvoice.number = invoice.id.toString()
        mustangInvoice.issueDate = invoice.invoiceDate
        mustangInvoice.dueDate = invoice.dueDate
        mustangInvoice.documentCode = invoice.invoiceTypeCode
        
        // Currency
        mustangInvoice.currency = invoice.currency
        
        // Seller
        mustangInvoice.sender = mapParty(seller)
        
        // Buyer
        mustangInvoice.recipient = mapParty(invoice.recipient)
        
        // References
        mustangInvoice.referenceNumber = invoice.buyerReference
        
        // Line items
        invoice.invoiceItems.forEach { item ->
            mustangInvoice.addItem(mapLineItem(item))
        }
        
        // VAT breakdown
        invoice.vatBreakdown.forEach { vat ->
            mustangInvoice.addVATPercent(
                BigDecimal(vat.vatRate),
                BigDecimal(vat.taxableAmount),
                BigDecimal(vat.taxAmount)
            )
        }
        
        return mustangInvoice
    }
    
    private fun mapParty(seller: UserConfigDTO): TradeParty {
        val party = TradeParty(
            seller.name,
            seller.street + " " + (seller.buildingNumber ?: ""),
            seller.postalCode,
            seller.city,
            seller.countryCode
        )
        
        // VAT ID
        if (seller.vatId.isNotBlank()) {
        party.addVATID(seller.vatId)
        }
        
        // Electronic address (email)
        party.electronicAddress = seller.email
        party.electronicAddressScheme = ZugferdConfig.EAS_EMAIL  // "9920"
        
        return party
    }
    
    private fun mapParty(recipient: RecipientDTO): TradeParty {
        val party = TradeParty(
            recipient.companyName,
            recipient.street + " " + (recipient.buildingNumber ?: ""),
            recipient.postalCode,
            recipient.city,
            recipient.countryCode
        )
        
        recipient.vatId?.let { party.addVATID(it) }
        
        party.electronicAddress = recipient.email
        party.electronicAddressScheme = ZugferdConfig.EAS_EMAIL
        
        return party
    }
    
    private fun mapLineItem(item: InvoiceItemDTO): Item {
        val lineItem = Item()
        lineItem.product = Product()
        lineItem.product.name = item.description
        lineItem.product.unit = item.unitCode
        
        lineItem.quantity = BigDecimal(item.quantity)
        lineItem.price = BigDecimal(item.netPrice)
        lineItem.lineTotalAmount = BigDecimal(item.lineNetAmount)
        
        // VAT for this line
        lineItem.product.VATPercent = BigDecimal(item.vatRate)
        
        return lineItem
    }
}
```

---

### 3.6 Integration with Existing Flow

#### 3.6.1 Updated PDF Generation Flow

```kotlin
// In InvoicePDFGenerator.kt or new service

fun generateInvoiceFiles(
    invoice: InvoiceDTO,
    seller: UserConfigDTO,
    basePath: String
): InvoiceFiles {
    val invoiceId = invoice.id.toString().padStart(6, '0')
    
    // 1. Generate PDF with existing OpenPDF (your design)
    val pdfPath = "$basePath/invoice-$invoiceId.pdf"
    invoiceToPDF(pdfPath, seller, invoice)
    
    // 2. Generate ZUGFeRD XML with Mustang
    val xmlPath = "$basePath/invoice-$invoiceId.xml"
    val generator = MustangZugferdGenerator()
    generator.generateXmlToFile(invoice, seller, xmlPath)
    
    // Return both paths
    return InvoiceFiles(pdfPath, xmlPath)
}

data class InvoiceFiles(
    val pdfPath: String,
    val xmlPath: String
)
```

#### 3.6.2 Email Integration

```kotlin
// Email both files
fun emailInvoice(files: InvoiceFiles, recipient: RecipientDTO, seller: UserConfigDTO) {
    val email = buildEmail {
        from(seller.email)
        to(recipient.email)
        subject("Invoice ${files.invoiceId}")
        body("Please find your invoice attached.")
        attachFile(files.pdfPath, "application/pdf")
        attachFile(files.xmlPath, "application/xml")  // or "text/xml"
    }
    sendEmail(email)
}
```

---

## 4. Phase 2: Hybrid PDF/A-3 (Embedded XML)

### 4.1 Overview

After Phase 1 is stable, embed the ZUGFeRD XML directly into the PDF to create a true hybrid invoice.

### 4.2 Implementation

```kotlin
// ZugferdPdfEmbedder.kt

class ZugferdPdfEmbedder {
    
    /**
     * Embed ZUGFeRD XML into existing PDF, creating PDF/A-3
     */
    fun embedZugferd(
        sourcePdfPath: String,
        xmlBytes: ByteArray,
        outputPath: String
    ) {
        PDDocument.load(File(sourcePdfPath)).use { doc ->
            
            // 1. Convert to PDF/A-3 compliant document
            addPdfA3Metadata(doc)
            
            // 2. Embed XML file
            embedXmlFile(doc, xmlBytes, ZugferdConfig.XML_FILENAME)
            
            // 3. Add XMP metadata for ZUGFeRD conformance
            addZugferdXmpMetadata(doc)
            
            // 4. Save
            doc.save(outputPath)
        }
    }
    
    private fun embedXmlFile(doc: PDDocument, xmlBytes: ByteArray, filename: String) {
        // Create embedded file specification
        val fileSpec = PDComplexFileSpecification()
        fileSpec.file = filename
        fileSpec.fileDescription = "ZUGFeRD Invoice"
        
        // Create embedded file stream
        val embeddedFile = PDEmbeddedFile(doc, ByteArrayInputStream(xmlBytes))
        embeddedFile.subtype = "text/xml"
        embeddedFile.size = xmlBytes.size
        embeddedFile.creationDate = Calendar.getInstance()
        embeddedFile.modDate = Calendar.getInstance()
        
        fileSpec.embeddedFile = embeddedFile
        
        // Add to document's embedded files
        val efTree = PDEmbeddedFilesNameTreeNode()
        val names = COSDictionary()
        names.setString(COSName.getPDFName(filename), fileSpec)
        efTree.names = names
        
        // Attach to document catalog
        val namesDic = doc.documentCatalog.names ?: PDDocumentNameDictionary(doc.documentCatalog)
        namesDic.embeddedFiles = efTree
        doc.documentCatalog.names = namesDic
        
        // Set as document-level attachment
        doc.documentCatalog.setItem(COSName.getPDFName("AF"), fileSpec)
    }
    
    private fun addZugferdXmpMetadata(doc: PDDocument) {
        // XMP metadata declaring PDF/A-3 and ZUGFeRD conformance
        val xmp = """
            <?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                        xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"
                        xmlns:zugferd="urn:ferd:pdfa:ZUGFeRD:2.2">
                        <pdfaid:part>3</pdfaid:part>
                        <pdfaid:conformance>B</pdfaid:conformance>
                        <zugferd:documentFileName>${ZugferdConfig.XML_FILENAME}</zugferd:documentFileName>
                        <zugferd:documentType>INVOICE</zugferd:documentType>
                        <zugferd:version>2.2</zugferd:version>
                        <zugferd:conformanceLevel>${ZugferdConfig.CONFORMANCE_LEVEL}</zugferd:conformanceLevel>
                    </rdf:Description>
                </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
        """.trimIndent()
        
        val metadata = PDMetadata(doc)
        metadata.importXMPMetadata(xmp.toByteArray(StandardCharsets.UTF_8))
        doc.documentCatalog.metadata = metadata
    }
}
```

### 4.3 Updated Flow (Phase 2)

```kotlin
fun generateZugferdPdf(
    invoice: InvoiceDTO,
    seller: UserConfigDTO,
    outputPath: String
) {
    // 1. Generate temp PDF
    val tempPdf = createTempFile("invoice", ".pdf")
    invoiceToPDF(tempPdf.absolutePath, seller, invoice)
    
    // 2. Generate XML
    val generator = MustangZugferdGenerator()
    val xmlBytes = generator.generateXml(invoice, seller)
    
    // 3. Embed XML into PDF/A-3
    val embedder = ZugferdPdfEmbedder()
    embedder.embedZugferd(tempPdf.absolutePath, xmlBytes, outputPath)
    
    // 4. Cleanup
    tempPdf.delete()
}
```

---

## 5. Data Migration Strategy

### 5.1 Problem

Existing data uses free-text address fields. New ZUGFeRD fields require structured data.

### 5.2 Migration Approach: Best-Effort Parsing + Manual Review

#### Step 1: Auto-Parse Existing Addresses

```kotlin
// MigrationService.kt

class AddressMigrationService {
    
    /**
     * Attempt to parse free-text address into structured fields
     * Returns success rate statistics
     */
    suspend fun migrateAddresses(): MigrationResult {
        val recipients = Repository.findAllRecipients()
        var successCount = 0
        var reviewCount = 0
        
        recipients.forEach { recipient ->
            val parsed = parseAddress(recipient.address ?: "", recipient.postal ?: "")
            
            if (parsed.confidence > 0.8) {
                // High confidence: auto-migrate
                Repository.updateRecipientStructuredAddress(recipient.id, parsed)
                successCount++
            } else {
                // Low confidence: flag for manual review
                Repository.flagForManualReview(recipient.id)
                reviewCount++
            }
        }
        
        return MigrationResult(successCount, reviewCount)
    }
    
    /**
     * Parse common Norwegian address formats:
     * - "Storgata 123, 0123 Oslo"
     * - "Storgata 123\n0123 Oslo"
     * - "Storgata 123, Oslo 0123"
     */
    private fun parseAddress(address: String, postal: String): ParsedAddress {
        // Implementation: regex-based parsing
        // Handle common Norwegian formats
        // Return structured fields + confidence score
    }
}
```

#### Step 2: Manual Review Terminal UI

```kotlin
// New terminal scene: AddressMigrationScene.kt

class AddressMigrationScene : MordantUIScene {
    // Show recipients needing manual review
    // Allow editing structured fields
    // Preview parsed address
    // Confirm or correct
}
```

#### Step 3: Seller Config Migration

For the seller's own address (one-time setup):
- Prompt on first run after update
- Pre-fill with parsed values from existing config
- Require confirmation before enabling ZUGFeRD

### 5.3 Backward Compatibility

```kotlin
// In RecipientDTO - maintain compatibility

fun getFullAddress(): String {
    // Use structured fields if available
    return if (street.isNotBlank()) {
        buildString {
            append(street)
            buildingNumber?.let { append(" ").append(it) }
            append(", ").append(postalCode)
            append(" ").append(city)
            append(", ").append(countryCode)
        }
    } else {
        // Fall back to legacy fields
        listOfNotNull(address, postal).joinToString("\n")
    }
}
```

---

## 6. Configuration Updates

### 6.1 InvoiceConfig Additions

```kotlin
@Serializable
data class InvoiceConfig(
    // Existing fields
    val dueDateOffset: Int = 14,
    val vatRate: Int = 0,
    val currency: String = "NOK",
    val language: String = "en",
    
    // NEW: ZUGFeRD settings
    val zugferdEnabled: Boolean = false,           // Feature toggle
    val zugferdPhase: String = "PHASE1",           // PHASE1=separate files, PHASE2=hybrid PDF
    val zugferdProfile: String = "EN16931",        // EN16931 recommended
    val defaultCountryCode: String = "NO",         // Default for new recipients
    val defaultPaymentMeans: String = "30",        // 30=Bank transfer
    val defaultVatCategory: String = "S",          // S=Standard
)
```

### 6.2 Environment/Defaults

```toml
# config.toml additions
[invoiceConfig]
zugferdEnabled = true
zugferdPhase = "PHASE1"
zugferdProfile = "EN16931"
defaultCountryCode = "NO"
defaultPaymentMeans = "30"
defaultVatCategory = "S"
```

---

## 7. Validation & Testing

### 7.1 Unit Testing

```kotlin
// ZugferdGeneratorTest.kt

@Test
fun `generate valid EN16931 invoice`() {
    val invoice = createTestInvoice()
    val seller = createTestSeller()
    
    val generator = MustangZugferdGenerator()
    val xml = generator.generateXml(invoice, seller)
    
    // Validate against schema
    val result = generator.validateXml(xml)
    assertTrue(result.isValid, result.errors.joinToString())
}

@Test
fun `mixed VAT categories are handled correctly`() {
    val invoice = createTestInvoiceWithMixedVat()
    // Standard (S) and Zero (Z) rates
    
    val xml = generator.generateXml(invoice, seller)
    // Assert VAT breakdown has both categories
}
```

### 7.2 External Validation Tools

1. **MustangProject CLI** (command line)
   ```bash
   java -jar mustang.jar --validate invoice.xml
   ```

2. **Quba Viewer** (GUI tool)
   - Open-source ZUGFeRD viewer
   - Shows both PDF and XML
   - Validates conformance

3. **Online Validators**
   - https://www.epoconsulting.com/portfolio/validation-tool-zugferd-en16931/
   - Upload XML for validation

### 7.3 Test Scenarios

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| Standard invoice | Single VAT rate, domestic | Valid EN16931 |
| Mixed VAT | Some items 0%, some 15% | Valid, multiple VAT categories |
| Export (EU) | Norwegian seller, German buyer, VAT 0% | Category Z with reason |
| Export (non-EU) | Norwegian seller, US buyer, VAT 0% | Category G |
| Credit note | invoiceTypeCode 381 | Valid credit note |
| XRechnung | With buyer reference | Valid for German B2G |
| Missing mandatory field | No VAT ID | Validation error |

---

## 8. Implementation Checklist

### Phase 1: Separate Files

- [ ] **Database Migrations**
  - [ ] Add structured address fields to RecipientsTable
  - [ ] Add structured address fields to UserConfigTable
  - [ ] Add ZUGFeRD metadata to InvoicesTable
  - [ ] Create InvoiceVatBreakdownTable
  - [ ] Update InvoiceItemsTable with line-level VAT

- [ ] **DTO Updates**
  - [ ] Update RecipientDTO with structured address
  - [ ] Update UserConfigDTO with structured address
  - [ ] Update InvoiceDTO with ZUGFeRD fields
  - [ ] Create VatBreakdownDTO
  - [ ] Update InvoiceItemDTO with line IDs and VAT

- [ ] **Validation Rules**
  - [ ] Country code validator (ISO 3166-1 alpha-2)
  - [ ] VAT ID format validator
  - [ ] IBAN format validator
  - [ ] Unit code validator (UN/ECE Rec 20)

- [ ] **ZUGFeRD Module**
  - [ ] Create zugferd package structure
  - [ ] Implement ZugferdGenerator with MustangProject
  - [ ] Implement ZugferdMapper
  - [ ] Add constants (VAT categories, unit codes, etc.)

- [ ] **Integration**
  - [ ] Update PDF generation flow
  - [ ] Add XML generation to invoice export
  - [ ] Update email to attach both files
  - [ ] Add configuration options

- [ ] **Migration Tool**
  - [ ] Create address parsing logic
  - [ ] Build terminal UI for manual review
  - [ ] Test migration on sample data

- [ ] **Testing**
  - [ ] Unit tests for generator
  - [ ] Integration tests
  - [ ] Validate output with external tools

### Phase 2: Hybrid PDF

- [ ] **PDF/A-3 Embedding**
  - [ ] Implement ZugferdPdfEmbedder with PDFBox
  - [ ] Add PDF/A-3 metadata
  - [ ] Add XMP metadata
  - [ ] Test with multiple PDF viewers

- [ ] **Integration**
  - [ ] Update generation flow to use embedder
  - [ ] Add configuration toggle (PHASE1/PHASE2)

- [ ] **Validation**
  - [ ] Validate hybrid PDFs with Quba
  - [ ] Test with government portals
  - [ ] Verify XML extraction works

---

## 9. Appendix

### 9.1 Common Code Lists

#### VAT Category Codes (BT-118)

| Code | Description | When to Use |
|------|-------------|-------------|
| S | Standard rate | Normal VAT rate applies |
| Z | Zero rated | Exports, intra-EU supplies |
| E | VAT exempt | Financial services, healthcare |
| G | Free export | Goods exported outside EU |
| O | Outside scope | Services outside VAT scope |
| K | Exempt for EEA | Intra-EU B2B supplies |

#### Common Payment Means Codes (BT-81)

| Code | Description |
|------|-------------|
| 10 | In cash |
| 30 | Credit transfer |
| 48 | Bank card |
| 49 | Direct debit |
| 50 | Cheque |
| 68 | Online payment service |

#### Common Unit Codes (UN/ECE Rec 20)

| Code | Description |
|------|-------------|
| C62 | Unit (one, piece) |
| KGM | Kilogram |
| LTR | Liter |
| HUR | Hour |
| DAY | Day |
| MTK | Square meter |
| MTR | Meter |

#### Invoice Type Codes (BT-3)

| Code | Description |
|------|-------------|
| 380 | Commercial invoice |
| 381 | Credit note |
| 384 | Corrected invoice |
| 389 | Self-billed invoice |

### 9.2 Useful Resources

- **ZUGFeRD Specification**: https://www.ferd-net.de/standards/zugferd
- **MustangProject**: https://www.mustangproject.org/zugferd/
- **EN16931 Code Lists**: https://ec.europa.eu/digital-building-blocks/wikis/display/DIGITAL/Registry+of+supporting+artefacts+to+implement+EN16931
- **Quba Viewer**: https://quba-viewer.org/

### 9.3 Contact

For questions about this implementation plan, refer to the architecture documentation or create an issue in the repository.

---

**End of Document**
