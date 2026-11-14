-- 初期データ投入 (再現性のため乱数シードを固定)
SELECT setseed(0.42);

-- 顧客 10,000 件
INSERT INTO customer (name, email, prefecture, created_at)
SELECT
  (ARRAY['佐藤','鈴木','高橋','田中','伊藤','渡辺','山本','中村','小林','加藤'])[1 + floor(random() * 10)::int]
    || ' ' ||
  (ARRAY['太郎','花子','一郎','美咲','健太','陽子','大輔','愛','翔太','結衣'])[1 + floor(random() * 10)::int],
  'user' || g || '@example.com',
  (ARRAY['北海道','宮城県','東京都','神奈川県','愛知県','大阪府','京都府','兵庫県','広島県','福岡県'])[1 + floor(random() * 10)::int],
  now() - (random() * interval '365 days')
FROM generate_series(1, 10000) AS g;

-- 商品 1,000 件
INSERT INTO product (name, category, price, stock)
SELECT
  cat || ' ' || g,
  cat,
  100 + floor(random() * 50000)::int,
  floor(random() * 500)::int
FROM generate_series(1, 1000) AS g
CROSS JOIN LATERAL (
  SELECT (ARRAY['食品','飲料','日用品','書籍','家電','衣類','文具','玩具'])[1 + (g % 8)] AS cat
) AS c;

-- 注文 50,000 件
INSERT INTO orders (customer_id, status, ordered_at)
SELECT
  1 + floor(random() * 10000)::int,
  (ARRAY['NEW','PAID','SHIPPED','DELIVERED','CANCELLED'])[1 + floor(random() * 5)::int],
  now() - (random() * interval '180 days')
FROM generate_series(1, 50000);

-- 注文明細 (1注文あたり 1〜4 件)
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
SELECT o.id, p.id, 1 + floor(random() * 5)::int, p.price
FROM orders o
CROSS JOIN LATERAL generate_series(1, 1 + (o.id % 4)) AS n
JOIN product p ON p.id = 1 + ((o.id * 7 + n * 13) % 1000);

-- 注文合計を明細から更新
UPDATE orders o
SET total = s.total
FROM (
  SELECT order_id, SUM(quantity * unit_price)::int AS total
  FROM order_item
  GROUP BY order_id
) s
WHERE o.id = s.order_id;

ANALYZE;
