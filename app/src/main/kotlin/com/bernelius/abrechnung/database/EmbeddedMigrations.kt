package com.bernelius.abrechnung.database

import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object EmbeddedMigrations {
    const val V1_USER_CONFIG = """
        CREATE TABLE IF NOT EXISTS user_config (
            id integer PRIMARY KEY,
            name text NOT NULL,
            address text NOT NULL,
            postal text NOT NULL,
            email text NOT NULL,
            account_number text NOT NULL,
            org_number text,
            smtp_host text,
            smtp_port text,
            smtp_user text,
            email_password text
        )
    """

    const val V1_RECIPIENTS = """
        CREATE TABLE IF NOT EXISTS recipients (
            id integer PRIMARY KEY AUTOINCREMENT,
            company_name text NOT NULL UNIQUE,
            address text NOT NULL,
            postal text NOT NULL,
            email text NOT NULL,
            org_number text UNIQUE
        )
    """

    const val V1_INVOICES = """
        CREATE TABLE IF NOT EXISTS invoices (
            id integer PRIMARY KEY AUTOINCREMENT,
            invoice_date text NOT NULL,
            due_date text NOT NULL,
            vat_rate integer NOT NULL DEFAULT 0,
            currency text NOT NULL,
            status text NOT NULL DEFAULT 'pending',
            recipient_id integer NOT NULL,
            FOREIGN KEY (recipient_id) REFERENCES recipients (id),
            CHECK (status IN ('pending', 'paid', 'failed', 'invalid'))
        )
    """

    const val V1_INVOICE_ITEMS = """
        CREATE TABLE IF NOT EXISTS invoice_items (
            id integer PRIMARY KEY AUTOINCREMENT,
            description text NOT NULL,
            quantity integer NOT NULL,
            unit_price real NOT NULL,
            discount integer NOT NULL DEFAULT 0,
            invoice_id integer NOT NULL,
            FOREIGN KEY (invoice_id) REFERENCES invoices (id)
        )
    """

    fun runMigrations() {
        transaction {
            exec(V1_USER_CONFIG)
            exec(V1_RECIPIENTS)
            exec(V1_INVOICES)
            exec(V1_INVOICE_ITEMS)
        }
    }
}