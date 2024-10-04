ALTER TABLE prisoner_differences ADD COLUMN label varchar(10) not null default 'GREENBLUE';

CREATE INDEX prisoner_differences_label_idx on prisoner_differences(label);
