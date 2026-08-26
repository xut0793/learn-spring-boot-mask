INSERT INTO users (id, name, phone, id_card, email, bank_card, city, address_detail, express_no) VALUES
(1, 'Zhang San', '13812345678', '110101199003078515', 'zhangsan@example.com', '6222021234567890123', 'Beijing', 'Chaoyang Road 88', 'SF1234567890123');

INSERT INTO contacts (id, user_id, type, contact_value) VALUES
(1, 1, 'home', '13900001111'),
(2, 1, 'work', '13700002222');
