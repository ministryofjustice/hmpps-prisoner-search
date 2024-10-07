ALTER TABLE prisoner_differences ADD COLUMN label varchar(10) not null default 'GREEN_BLUE';

DROP INDEX prisoner_differences_date_time_idx;
DROP INDEX prisoner_differences_noms_number_idx;
CREATE INDEX prisoner_differences_date_time_idx on prisoner_differences(label, date_time);
CREATE INDEX prisoner_differences_noms_number_idx on prisoner_differences(noms_number);
