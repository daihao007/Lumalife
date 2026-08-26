-- Development/test data only. Never run this script in production.
-- Passwords are BCrypt hashes compatible with Spring Security.
-- USER/MERCHANT_ADMIN password: abc123456; PLATFORM_ADMIN password: admin123456.

START TRANSACTION;

INSERT INTO category (id, name, icon) VALUES
  (1, '川湘菜', 'flame'),
  (2, '咖啡茶饮', 'coffee'),
  (3, '轻食简餐', 'salad'),
  (4, '甜品烘焙', 'cake'),
  (5, '生活服务', 'sparkle')
ON DUPLICATE KEY UPDATE name = VALUES(name), icon = VALUES(icon), is_deleted = 0;

INSERT INTO merchant (id, category_id, name, cover_url, avg_score, avg_price_cent, monthly_sales, distance_km, status, address, recommend_reason) VALUES
  (1, 1, '巷口川味研究所', 'https://images.unsplash.com/photo-1585032226651-759b368d7246?auto=format&fit=crop&w=1000&q=80', 4.80, 3800, 386, 1.20, '营业中', '梧桐路 18 号', '评分高、距离近、近期销量较好'),
  (2, 2, '晨雾咖啡局', 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=1000&q=80', 4.70, 3200, 268, 0.70, '营业中', '湖畔街 3 号', '距离近、评价稳定'),
  (3, 3, '绿盒轻食', 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=1000&q=80', 4.50, 2900, 189, 2.40, '营业中', '学院路 66 号', '热量标注清晰、复购高'),
  (4, 4, '栗香烘焙室', 'https://images.unsplash.com/photo-1486427944299-d1955d23e34d?auto=format&fit=crop&w=1000&q=80', 4.60, 2600, 142, 3.10, '营业中', '银杏街 9 号', '甜品评分高')
ON DUPLICATE KEY UPDATE category_id = VALUES(category_id), name = VALUES(name), cover_url = VALUES(cover_url), avg_score = VALUES(avg_score), avg_price_cent = VALUES(avg_price_cent), monthly_sales = VALUES(monthly_sales), distance_km = VALUES(distance_km), status = VALUES(status), address = VALUES(address), recommend_reason = VALUES(recommend_reason), is_deleted = 0;

INSERT INTO user_account (id, phone, password_hash, nickname, avatar_url, role, merchant_id) VALUES
  (1, '13800000001', '$2a$10$Srh6L3GMusEoK/Y3Plgew.2jd1Xnl3BxlgKYHrE6B35wtxv2XdK86', '林夏', '', 'USER', NULL),
  (2, '13800000002', '$2a$10$Srh6L3GMusEoK/Y3Plgew.2jd1Xnl3BxlgKYHrE6B35wtxv2XdK86', '巷口川味研究所', '', 'MERCHANT_ADMIN', 1),
  (3, '13800000003', '$2a$10$Srh6L3GMusEoK/Y3Plgew.2jd1Xnl3BxlgKYHrE6B35wtxv2XdK86', '晨雾咖啡局', '', 'MERCHANT_ADMIN', 2),
  (4, '13800000004', '$2a$10$Srh6L3GMusEoK/Y3Plgew.2jd1Xnl3BxlgKYHrE6B35wtxv2XdK86', '绿盒轻食', '', 'MERCHANT_ADMIN', 3),
  (5, '13800000005', '$2a$10$Srh6L3GMusEoK/Y3Plgew.2jd1Xnl3BxlgKYHrE6B35wtxv2XdK86', '栗香烘焙室', '', 'MERCHANT_ADMIN', 4),
  (6, '13800000000', '$2a$10$jwQglpDS.SX8c3HHTGCHxOUTThvoBnn5Lb7SKnPnuNUfyS/virVGe', '平台管理员', '', 'PLATFORM_ADMIN', NULL)
ON DUPLICATE KEY UPDATE phone = VALUES(phone), password_hash = VALUES(password_hash), nickname = VALUES(nickname), avatar_url = VALUES(avatar_url), role = VALUES(role), merchant_id = VALUES(merchant_id), is_deleted = 0;

INSERT INTO user_address (id, user_id, contact_name, phone, detail, is_default) VALUES
  (101, 1, '林夏', '13800000001', '梧桐路 18 号 2 单元 601', 1),
  (102, 1, '林夏', '13800000001', '学院路 66 号软件楼', 0)
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), contact_name = VALUES(contact_name), phone = VALUES(phone), detail = VALUES(detail), is_default = VALUES(is_default), is_deleted = 0;

INSERT INTO product (id, merchant_id, name, description, price_cent, stock, is_listed) VALUES
  (1001, 1, '藤椒鸡饭', '麻香鲜亮，适合午餐', 2680, 99, 1),
  (1002, 1, '毛血旺小锅', '课程演示热门搜索菜', 4280, 99, 1),
  (1003, 1, '冰粉', '解辣甜品', 900, 99, 1),
  (1004, 2, '桂花拿铁', '轻甜花香', 2800, 99, 1),
  (1005, 2, '冷萃咖啡', '低酸清爽', 2600, 99, 1),
  (1006, 3, '牛油果鸡胸碗', '高蛋白轻食', 3280, 99, 1),
  (1007, 4, '栗子巴斯克', '招牌切块', 2200, 99, 1)
ON DUPLICATE KEY UPDATE merchant_id = VALUES(merchant_id), name = VALUES(name), description = VALUES(description), price_cent = VALUES(price_cent), stock = VALUES(stock), is_listed = VALUES(is_listed), is_deleted = 0;

INSERT INTO group_deal (id, merchant_id, title, description, price_cent, stock, is_active) VALUES
  (1, 1, '双人川味到店套餐', '2 道主菜 + 2 杯饮品', 6990, 30, 1),
  (2, 2, '咖啡下午茶券', '任意两杯咖啡 + 甜点', 4990, 45, 1),
  (3, 4, '烘焙分享盒', '6 款切块组合', 5990, 20, 1)
ON DUPLICATE KEY UPDATE merchant_id = VALUES(merchant_id), title = VALUES(title), description = VALUES(description), price_cent = VALUES(price_cent), stock = VALUES(stock), is_active = VALUES(is_active), is_deleted = 0;

COMMIT;
