CREATE TABLE incentive_hash
(
    prisoner_number   varchar(7)
        constraint incentive_prisoner_number_pk PRIMARY KEY,
    incentive_hash    varchar(24),
    updated_date_time timestamp
);
