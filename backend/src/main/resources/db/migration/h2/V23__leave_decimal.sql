-- V23 (H2) 假期账本 decimal 化（half_day/hour 支持 0.5/2.5 等非整数；H2 2.x 语法）
ALTER TABLE leave_balance ALTER COLUMN quota SET DATA TYPE NUMERIC(10,2);
ALTER TABLE leave_balance ALTER COLUMN used SET DATA TYPE NUMERIC(10,2);
ALTER TABLE leave_balance ALTER COLUMN frozen SET DATA TYPE NUMERIC(10,2);
ALTER TABLE leave_transaction ALTER COLUMN delta SET DATA TYPE NUMERIC(10,2);
