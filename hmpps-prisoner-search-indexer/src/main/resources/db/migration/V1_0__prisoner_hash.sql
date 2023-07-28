CREATE TABLE prisoner_hash
(
    prisoner_number   varchar(7)
        constraint prisoner_number_pk PRIMARY KEY,
    prisoner_hash     varchar(24),
    updated_date_time timestamp
);
