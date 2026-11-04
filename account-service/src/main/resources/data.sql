ALTER TABLE account AUTO_INCREMENT = 1000;
DELETE FROM account;
INSERT INTO account
    (id, user_id, account_type, account_number, balance, currency)
VALUES
    (1,  'user1',  'CHECKING', '10000001018', 100000.00, 'USD'),
    (2,  'user2',  'CHECKING', '10000001019', 100000.00, 'USD'),
    (3,  'user3',  'CHECKING', '10000001020', 100000.00, 'USD'),
    (4,  'user4',  'CHECKING', '10000001021', 100000.00, 'USD'),
    (5,  'user5',  'CHECKING', '10000001022', 100000.00, 'USD'),
    (6,  'user6',  'CHECKING', '10000001023', 100000.00, 'USD'),
    (7,  'user7',  'CHECKING', '10000001024', 100000.00, 'USD'),
    (8,  'user8',  'CHECKING', '10000001025', 100000.00, 'USD'),
    (9,  'user9',  'CHECKING', '10000001026', 100000.00, 'USD'),
    (10, 'user10', 'CHECKING', '10000001027', 100000.00, 'USD'),
    (11, 'user11', 'CHECKING', '10000001028', 100000.00, 'USD'),
    (12, 'user12', 'CHECKING', '10000001029', 100000.00, 'USD'),
    (13, 'user13', 'CHECKING', '10000001030', 100000.00, 'USD')
AS incoming
ON DUPLICATE KEY UPDATE
    user_id = incoming.user_id,
    account_number = incoming.account_number;

