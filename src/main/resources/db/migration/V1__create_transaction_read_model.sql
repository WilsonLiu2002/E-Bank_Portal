create table account_ownership (
    iban varchar(64) primary key,
    customer_id varchar(64) not null,
    currency char(3) not null
);

create index idx_account_ownership_customer on account_ownership (customer_id);

create table money_account_transaction (
    transaction_id varchar(80) primary key,
    customer_id varchar(64) not null,
    amount numeric(19, 4) not null,
    currency char(3) not null,
    account_iban varchar(64) not null references account_ownership (iban),
    value_date date not null,
    description varchar(512) not null,
    ingested_at timestamp with time zone not null,
    record_version bigint not null default 0
);

create index idx_transaction_customer_month
    on money_account_transaction (customer_id, value_date desc, transaction_id);

create index idx_transaction_iban_value_date
    on money_account_transaction (account_iban, value_date desc);
