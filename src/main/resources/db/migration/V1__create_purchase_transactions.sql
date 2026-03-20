CREATE TABLE public.purchase_transactions (
    id UUID PRIMARY KEY,
    description VARCHAR(50) NOT NULL,
    transaction_date DATE NOT NULL,
    purchase_amount_usd NUMERIC(19, 2) NOT NULL
);
