CREATE TABLE user_config (
    id integer PRIMARY KEY,
    name varchar(50) NOT NULL,
    address varchar(80) NOT NULL,
    postal varchar(80) NOT NULL,
    email varchar(255) NOT NULL,
    account_number varchar(255) NOT NULL,
    org_number varchar(20),
    smtp_host varchar(255),
    smtp_port char(4),
    smtp_user varchar(255),
    email_password varchar(255)
);

CREATE TABLE recipients (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_name varchar(255) NOT NULL UNIQUE,
    address varchar(255) NOT NULL,
    postal varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    org_number varchar(20) UNIQUE
);

CREATE TABLE invoices (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_date date NOT NULL,
    due_date date NOT NULL,
    vat_rate integer NOT NULL DEFAULT 0,
    currency varchar(10) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'pending',
    recipient_id integer NOT NULL,
    CONSTRAINT fk_invoices_recipient FOREIGN KEY (recipient_id) REFERENCES recipients (id),
    CONSTRAINT chk_invoice_status CHECK (status IN ('pending', 'paid', 'failed', 'invalid'))
);

CREATE TABLE invoice_items (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    description varchar(255) NOT NULL,
    quantity integer NOT NULL,
    unit_price double precision NOT NULL,
    discount integer NOT NULL DEFAULT 0,
    invoice_id integer NOT NULL,
    CONSTRAINT fk_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
);

