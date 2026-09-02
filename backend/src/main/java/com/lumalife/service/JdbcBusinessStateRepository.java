package com.lumalife.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * MySQL adapter backed by the normalized V001 business tables.
 *
 * <p>The port still exchanges one aggregate document with the monolith domain
 * service so the HTTP/domain contract remains unchanged. The JSON document is
 * never the steady-state database representation: load materializes it from
 * relational rows and save writes all mutable areas in one transaction. A
 * legacy V003 {@code business_state} row is imported once on first startup.</p>
 */
@Repository
@Profile({"monolith", "migration"})
@ConditionalOnProperty(name = "lumalife.persistence", havingValue = "mysql")
public class JdbcBusinessStateRepository implements BusinessStateRepository {
  private static final String LOCK_NAME = "lumalife-relational-business-state";
  private final String url;
  private final String username;
  private final String password;
  private final ObjectMapper mapper;

  public JdbcBusinessStateRepository(
      @Value("${lumalife.mysql.url}") String url,
      @Value("${lumalife.mysql.username}") String username,
      @Value("${lumalife.mysql.password}") String password,
      ObjectMapper mapper) {
    this.url = url;
    this.username = username;
    this.password = password;
    this.mapper = mapper;
  }

  @Override
  public Optional<String> load() {
    try (Connection connection = open()) {
      Optional<String> legacy = legacySnapshot(connection);
      if (legacy.isPresent()) {
        save(legacy.get());
        return legacy;
      }
      if (scalarLong(connection, "SELECT COUNT(*) FROM user_account WHERE is_deleted = 0") == 0) {
        return Optional.empty();
      }
      return Optional.of(mapper.writeValueAsString(readState(connection)));
    } catch (Exception error) {
      throw new IllegalStateException("Failed to load LumaLife business state from relational MySQL tables", error);
    }
  }

  @Override
  public void save(String payload) {
    try (Connection connection = open()) {
      connection.setAutoCommit(false);
      connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      boolean locked = false;
      try {
        locked = acquireLock(connection);
        if (!locked) throw new SQLException("Timed out acquiring MySQL business-state writer lock");
        JsonNode state = mapper.readTree(payload);
        clearMutableTables(connection);
        writeCategories(connection, state);
        writeMerchants(connection, state.path("accounts"));
        writeAccounts(connection, state.path("accounts"));
        writeAddresses(connection, state.path("addresses"));
        writeProducts(connection, state.path("products"));
        writeDeals(connection, state.path("deals"));
        writeCarts(connection, state.path("carts"));
        Set<Long> addressIds = new HashSet<>();
        state.path("addresses").forEach(address -> addressIds.add(address.path("id").asLong()));
        Map<Long, Long> orderUsers = writeOrders(connection, state.path("orders"), addressIds);
        writeReviews(connection, state.path("reviews"), orderUsers);
        writeFavorites(connection, state.path("favorites"));
        writeConversations(connection, state.path("conversations"));
        writeLogs(connection, state.path("logs"));
        execute(connection, "DELETE FROM business_state WHERE state_key = 'primary'");
        connection.commit();
      } catch (Exception error) {
        connection.rollback();
        throw error;
      } finally {
        if (locked) releaseLock(connection);
      }
    } catch (Exception error) {
      throw new IllegalStateException("Failed to save LumaLife business state to relational MySQL tables", error);
    }
  }

  private ObjectNode readState(Connection connection) throws SQLException {
    ObjectNode state = mapper.createObjectNode();
    Map<Long, ObjectNode> merchants = readMerchants(connection);
    ArrayNode categories = state.putArray("categories");
    query(connection, "SELECT id,name,icon FROM category WHERE is_deleted=0 ORDER BY id", row -> {
      ObjectNode category=categories.addObject(); category.put("id",row.getLong("id"));
      category.put("name",row.getString("name")); category.put("icon",row.getString("icon"));
    });
    ArrayNode accounts = state.putArray("accounts");
    query(connection, "SELECT id,phone,password_hash,nickname,avatar_url,role,merchant_id FROM user_account WHERE is_deleted=0 ORDER BY id", row -> {
      ObjectNode account = accounts.addObject();
      account.put("id", row.getLong("id"));
      account.put("phone", row.getString("phone"));
      account.put("password", row.getString("password_hash"));
      account.put("nickname", row.getString("nickname"));
      account.put("avatarUrl", row.getString("avatar_url"));
      account.put("role", row.getString("role"));
      long merchantId = row.getLong("merchant_id");
      if (row.wasNull()) {
        account.putNull("merchantId");
        account.putNull("merchant");
      } else {
        account.put("merchantId", merchantId);
        account.set("merchant", merchants.get(merchantId));
      }
    });

    ArrayNode addresses = state.putArray("addresses");
    query(connection, "SELECT id,user_id,contact_name,phone,detail,is_default FROM user_address WHERE is_deleted=0 ORDER BY id", row -> {
      ObjectNode address = addresses.addObject();
      address.put("id", row.getLong("id")); address.put("userId", row.getLong("user_id"));
      address.put("contactName", row.getString("contact_name")); address.put("phone", row.getString("phone"));
      address.put("detail", row.getString("detail")); address.put("defaultAddress", row.getBoolean("is_default"));
    });
    readProducts(connection, state.putArray("products"));
    readDeals(connection, state.putArray("deals"));
    readCarts(connection, state.putObject("carts"));
    Map<Long, ObjectNode> orders = readOrders(connection, state.putArray("orders"));
    readOrderChildren(connection, orders);
    readReviews(connection, state.putArray("reviews"));
    readConversations(connection, state.putArray("conversations"));
    readFavorites(connection, state.putObject("favorites"));
    readLogs(connection, state.putArray("logs"));

    ArrayNode profiles = state.putArray("merchantProfiles");
    for (JsonNode account : accounts) {
      if ("MERCHANT_ADMIN".equals(account.path("role").asText()) && account.hasNonNull("merchantId")) {
        ObjectNode profile = profiles.addObject();
        profile.put("phone", account.path("phone").asText());
        profile.put("merchantId", account.path("merchantId").asLong());
        profile.put("nickname", account.path("nickname").asText());
      }
    }
    return state;
  }

  private Map<Long, ObjectNode> readMerchants(Connection connection) throws SQLException {
    Map<Long, ObjectNode> result = new LinkedHashMap<>();
    query(connection, "SELECT m.id,m.name,m.category_id,c.name category_name,m.cover_url,m.avg_score,m.avg_price_cent,m.monthly_sales,m.distance_km,m.status,m.address,m.recommend_reason FROM merchant m JOIN category c ON c.id=m.category_id WHERE m.is_deleted=0 ORDER BY m.id", row -> {
      ObjectNode merchant = mapper.createObjectNode();
      long id = row.getLong("id"); merchant.put("id", id); merchant.put("name", row.getString("name"));
      merchant.put("categoryId", row.getLong("category_id")); merchant.put("categoryName", row.getString("category_name"));
      merchant.put("cover", row.getString("cover_url")); merchant.put("avgScore", row.getDouble("avg_score"));
      merchant.put("avgPrice", row.getLong("avg_price_cent") / 100); merchant.put("monthlySales", row.getInt("monthly_sales"));
      merchant.put("distanceKm", row.getDouble("distance_km")); merchant.put("status", row.getString("status"));
      merchant.put("address", row.getString("address")); merchant.put("reason", row.getString("recommend_reason"));
      result.put(id, merchant);
    });
    return result;
  }

  private void readProducts(Connection c, ArrayNode target) throws SQLException {
    query(c, "SELECT id,merchant_id,name,description,price_cent,stock,is_listed FROM product WHERE is_deleted=0 ORDER BY id", r -> {
      ObjectNode n=target.addObject(); n.put("id",r.getLong("id")); n.put("merchantId",r.getLong("merchant_id"));
      n.put("name",r.getString("name")); n.put("description",r.getString("description")); n.put("priceCent",r.getLong("price_cent"));
      n.put("stock",r.getInt("stock")); n.put("listed",r.getBoolean("is_listed"));
    });
  }

  private void readDeals(Connection c, ArrayNode target) throws SQLException {
    query(c, "SELECT id,merchant_id,title,description,price_cent,stock,is_active FROM group_deal WHERE is_deleted=0 ORDER BY id", r -> {
      ObjectNode n=target.addObject(); n.put("id",r.getLong("id")); n.put("merchantId",r.getLong("merchant_id"));
      n.put("title",r.getString("title")); n.put("description",r.getString("description")); n.put("priceCent",r.getLong("price_cent"));
      n.put("stock",r.getInt("stock")); n.put("active",r.getBoolean("is_active"));
    });
  }

  private void readCarts(Connection c, ObjectNode target) throws SQLException {
    query(c, "SELECT user_id,product_id,quantity FROM cart_item ORDER BY user_id,id", r -> {
      String key=Long.toString(r.getLong("user_id")); ArrayNode items=target.has(key)?(ArrayNode)target.get(key):target.putArray(key);
      ObjectNode n=items.addObject(); n.put("productId",r.getLong("product_id")); n.put("quantity",r.getInt("quantity"));
    });
  }

  private Map<Long, ObjectNode> readOrders(Connection c, ArrayNode target) throws SQLException {
    Map<Long,ObjectNode> result=new LinkedHashMap<>();
    query(c, "SELECT id,user_id,merchant_id,merchant_name_snapshot,order_type,status,total_cent,address_id,address_snapshot,is_reviewed,is_stock_deducted,created_at FROM order_main WHERE is_deleted=0 ORDER BY id", r -> {
      ObjectNode n=target.addObject(); long id=r.getLong("id"); n.put("id",id); n.put("userId",r.getLong("user_id"));
      n.put("merchantId",r.getLong("merchant_id")); n.put("merchantName",r.getString("merchant_name_snapshot")); n.put("type",r.getString("order_type"));
      n.put("status",r.getString("status")); n.put("totalCent",r.getLong("total_cent")); n.putNull("clientRequestId"); n.putNull("couponCode");
      long addressId=r.getLong("address_id"); if(r.wasNull())n.putNull("addressId");else n.put("addressId",addressId);
      putNullable(n,"addressSnapshot",r.getString("address_snapshot")); n.put("reviewed",r.getBoolean("is_reviewed"));
      n.put("stockDeducted",r.getBoolean("is_stock_deducted")); n.put("createdAt",r.getTimestamp("created_at").toLocalDateTime().toString());
      n.putArray("lines"); n.putObject("statusTimeline"); result.put(id,n);
    });
    return result;
  }

  private void readOrderChildren(Connection c, Map<Long,ObjectNode> orders) throws SQLException {
    query(c, "SELECT order_id,item_id,item_name_snapshot,quantity,unit_price_cent FROM order_item ORDER BY id", r -> {
      ObjectNode order=orders.get(r.getLong("order_id")); if(order==null)return; ObjectNode n=((ArrayNode)order.get("lines")).addObject();
      long itemId=r.getLong("item_id"); if(r.wasNull())n.putNull("itemId");else n.put("itemId",itemId);
      n.put("name",r.getString("item_name_snapshot")); n.put("quantity",r.getInt("quantity")); n.put("priceCent",r.getLong("unit_price_cent"));
    });
    query(c, "SELECT order_id,status,occurred_at FROM order_status_timeline ORDER BY id", r -> {
      ObjectNode order=orders.get(r.getLong("order_id")); if(order!=null)((ObjectNode)order.get("statusTimeline")).put(r.getString("status"),r.getTimestamp("occurred_at").toLocalDateTime().toString());
    });
    query(c, "SELECT order_id,client_request_id FROM payment_record WHERE status='SUCCESS' ORDER BY id", r -> {
      ObjectNode order=orders.get(r.getLong("order_id")); if(order!=null)order.put("clientRequestId",r.getString("client_request_id"));
    });
    query(c, "SELECT order_id,code FROM coupon ORDER BY id", r -> { ObjectNode order=orders.get(r.getLong("order_id")); if(order!=null)order.put("couponCode",r.getString("code")); });
  }

  private void readReviews(Connection c, ArrayNode target) throws SQLException {
    query(c, "SELECT id,order_id,merchant_id,user_name_snapshot,score,taste_score,service_score,content,created_at FROM review WHERE is_deleted=0 ORDER BY id", r -> {
      ObjectNode n=target.addObject(); n.put("id",r.getLong("id")); n.put("orderId",r.getLong("order_id")); n.put("merchantId",r.getLong("merchant_id"));
      n.put("userName",r.getString("user_name_snapshot")); n.put("score",r.getInt("score")); n.put("tasteScore",r.getInt("taste_score"));
      n.put("serviceScore",r.getInt("service_score")); n.put("content",r.getString("content")); n.put("createdAt",r.getTimestamp("created_at").toLocalDateTime().toString());
    });
  }

  private void readConversations(Connection c, ArrayNode target) throws SQLException {
    Map<String,ArrayNode> groups=new LinkedHashMap<>();
    query(c, "SELECT id,user_id,merchant_id,sender_role,sender_name,content,created_at FROM chat_message ORDER BY user_id,merchant_id,created_at,id", r -> {
      String key=r.getLong("user_id")+":"+r.getLong("merchant_id"); ArrayNode messages=groups.computeIfAbsent(key,k->target.addObject().putArray("messages"));
      ObjectNode n=messages.addObject(); n.put("id",r.getLong("id")); n.put("userId",r.getLong("user_id")); n.put("merchantId",r.getLong("merchant_id"));
      n.put("senderRole",fromDatabaseSenderRole(r.getString("sender_role"))); n.put("senderName",r.getString("sender_name")); n.put("content",r.getString("content"));
      n.put("createdAt",r.getTimestamp("created_at").toLocalDateTime().toString());
    });
  }

  private void readFavorites(Connection c, ObjectNode target) throws SQLException {
    query(c, "SELECT user_id,merchant_id FROM merchant_favorite ORDER BY user_id,merchant_id", r -> {
      String key=Long.toString(r.getLong("user_id")); ArrayNode ids=target.has(key)?(ArrayNode)target.get(key):target.putArray(key); ids.add(r.getLong("merchant_id"));
    });
  }

  private void readLogs(Connection c, ArrayNode target) throws SQLException {
    query(c, "SELECT id,actor,action,created_at FROM operation_log ORDER BY id", r -> {
      ObjectNode n=target.addObject(); n.put("id",r.getLong("id")); n.put("actor",r.getString("actor")); n.put("action",r.getString("action"));
      n.put("createdAt",r.getTimestamp("created_at").toLocalDateTime().toString());
    });
  }

  private void clearMutableTables(Connection c) throws SQLException {
    for (String table : new String[]{"chat_message","merchant_favorite","review","coupon","payment_record","order_status_timeline","order_item","order_main","cart_item","group_deal","product","auth_session","user_address","user_account","merchant","category","operation_log"}) execute(c,"DELETE FROM "+table);
  }

  private void writeCategories(Connection c, JsonNode state) throws SQLException {
    Map<Long,String> categories=new LinkedHashMap<>();
    Map<Long,String> icons=new LinkedHashMap<>();
    for(JsonNode category:state.path("categories")){
      categories.put(category.path("id").asLong(),category.path("name").asText("未分类"));
      icons.put(category.path("id").asLong(),category.path("icon").asText());
    }
    if(categories.isEmpty())for(JsonNode account:state.path("accounts"))if(account.hasNonNull("merchant")){
      JsonNode merchant=account.path("merchant"); long id=merchant.path("categoryId").asLong(1);
      categories.put(id,merchant.path("categoryName").asText("未分类")); icons.put(id,"");
    }
    String sql="INSERT INTO category(id,name,icon) VALUES(?,?,?)";
    for(Map.Entry<Long,String> entry:categories.entrySet())update(c,sql,p->{p.setLong(1,entry.getKey());p.setString(2,entry.getValue());p.setString(3,icons.getOrDefault(entry.getKey(),""));});
  }

  private void writeMerchants(Connection c, JsonNode accounts) throws SQLException {
    Map<Long,JsonNode> unique=new LinkedHashMap<>();
    for(JsonNode account:accounts)if(account.hasNonNull("merchant"))unique.put(account.path("merchant").path("id").asLong(),account.path("merchant"));
    String sql="INSERT INTO merchant(id,category_id,name,cover_url,avg_score,avg_price_cent,monthly_sales,distance_km,status,address,recommend_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
    for(JsonNode n:unique.values()) update(c,sql,p->{p.setLong(1,n.path("id").asLong());p.setLong(2,n.path("categoryId").asLong(1));p.setString(3,n.path("name").asText());p.setString(4,n.path("cover").asText());p.setDouble(5,n.path("avgScore").asDouble());p.setLong(6,n.path("avgPrice").asLong()*100);p.setInt(7,n.path("monthlySales").asInt());p.setDouble(8,n.path("distanceKm").asDouble());p.setString(9,n.path("status").asText());p.setString(10,n.path("address").asText());p.setString(11,n.path("reason").asText());});
  }

  private void writeAccounts(Connection c, JsonNode nodes) throws SQLException {
    String sql="INSERT INTO user_account(id,phone,password_hash,nickname,avatar_url,role,merchant_id) VALUES(?,?,?,?,?,?,?)";
    for(JsonNode n:nodes)update(c,sql,p->{p.setLong(1,n.path("id").asLong());p.setString(2,n.path("phone").asText());p.setString(3,n.path("password").asText());p.setString(4,n.path("nickname").asText());p.setString(5,n.path("avatarUrl").asText());p.setString(6,n.path("role").asText());setNullableLong(p,7,n.get("merchantId"));});
  }

  private void writeAddresses(Connection c, JsonNode nodes) throws SQLException {
    String sql="INSERT INTO user_address(id,user_id,contact_name,phone,detail,is_default) VALUES(?,?,?,?,?,?)";
    for(JsonNode n:nodes)update(c,sql,p->{p.setLong(1,n.path("id").asLong());p.setLong(2,n.path("userId").asLong());p.setString(3,n.path("contactName").asText());p.setString(4,n.path("phone").asText());p.setString(5,n.path("detail").asText());p.setBoolean(6,n.path("defaultAddress").asBoolean());});
  }

  private void writeProducts(Connection c, JsonNode nodes) throws SQLException {
    String sql="INSERT INTO product(id,merchant_id,name,description,price_cent,stock,is_listed) VALUES(?,?,?,?,?,?,?)";
    for(JsonNode n:nodes)update(c,sql,p->{p.setLong(1,n.path("id").asLong());p.setLong(2,n.path("merchantId").asLong());p.setString(3,n.path("name").asText());p.setString(4,n.path("description").asText());p.setLong(5,n.path("priceCent").asLong());p.setInt(6,n.path("stock").asInt());p.setBoolean(7,n.path("listed").asBoolean());});
  }

  private void writeDeals(Connection c, JsonNode nodes) throws SQLException {
    String sql="INSERT INTO group_deal(id,merchant_id,title,description,price_cent,stock,is_active) VALUES(?,?,?,?,?,?,?)";
    for(JsonNode n:nodes)update(c,sql,p->{p.setLong(1,n.path("id").asLong());p.setLong(2,n.path("merchantId").asLong());p.setString(3,n.path("title").asText());p.setString(4,n.path("description").asText());p.setLong(5,n.path("priceCent").asLong());p.setInt(6,n.path("stock").asInt());p.setBoolean(7,n.path("active").asBoolean());});
  }

  private void writeCarts(Connection c, JsonNode carts) {
    String sql="INSERT INTO cart_item(user_id,product_id,quantity) VALUES(?,?,?)";
    carts.fields().forEachRemaining(entry->{for(JsonNode n:entry.getValue())uncheckedUpdate(c,sql,p->{p.setLong(1,Long.parseLong(entry.getKey()));p.setLong(2,n.path("productId").asLong());p.setInt(3,n.path("quantity").asInt());});});
  }

  private Map<Long,Long> writeOrders(Connection c, JsonNode nodes, Set<Long> addressIds) throws SQLException {
    Map<Long,Long> users=new LinkedHashMap<>();
    String main="INSERT INTO order_main(id,user_id,merchant_id,merchant_name_snapshot,order_type,status,total_cent,address_id,address_snapshot,is_reviewed,is_stock_deducted,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
    String item="INSERT INTO order_item(order_id,item_type,item_id,item_name_snapshot,quantity,unit_price_cent) VALUES(?,?,?,?,?,?)";
    String timeline="INSERT INTO order_status_timeline(order_id,status,occurred_at) VALUES(?,?,?)";
    String payment="INSERT INTO payment_record(user_id,order_id,client_request_id,amount_cent,status,paid_at) VALUES(?,?,?,?,?,?)";
    String coupon="INSERT INTO coupon(order_id,merchant_id,code,status,redeemed_at) VALUES(?,?,?,?,?)";
    for(JsonNode n:nodes){long id=n.path("id").asLong(),userId=n.path("userId").asLong();users.put(id,userId);LocalDateTime created=parseTime(n.path("createdAt"));
      update(c,main,p->{p.setLong(1,id);p.setLong(2,userId);p.setLong(3,n.path("merchantId").asLong());p.setString(4,n.path("merchantName").asText());p.setString(5,n.path("type").asText());p.setString(6,n.path("status").asText());p.setLong(7,n.path("totalCent").asLong());if(n.hasNonNull("addressId")&&addressIds.contains(n.path("addressId").asLong()))p.setLong(8,n.path("addressId").asLong());else p.setNull(8,java.sql.Types.BIGINT);setNullableString(p,9,n.get("addressSnapshot"));p.setBoolean(10,n.path("reviewed").asBoolean());p.setBoolean(11,n.path("stockDeducted").asBoolean());p.setTimestamp(12,Timestamp.valueOf(created));p.setTimestamp(13,Timestamp.valueOf(created));});
      for(JsonNode line:n.path("lines"))update(c,item,p->{p.setLong(1,id);p.setString(2,"GROUP_BUY".equals(n.path("type").asText())?"GROUP_DEAL":"PRODUCT");setNullableLong(p,3,line.get("itemId"));p.setString(4,line.path("name").asText());p.setInt(5,line.path("quantity").asInt());p.setLong(6,line.path("priceCent").asLong());});
      n.path("statusTimeline").fields().forEachRemaining(e->uncheckedUpdate(c,timeline,p->{p.setLong(1,id);p.setString(2,e.getKey());p.setTimestamp(3,Timestamp.valueOf(parseTime(e.getValue())));}));
      if(n.hasNonNull("clientRequestId"))update(c,payment,p->{p.setLong(1,userId);p.setLong(2,id);p.setString(3,n.path("clientRequestId").asText());p.setLong(4,n.path("totalCent").asLong());p.setString(5,"SUCCESS");p.setTimestamp(6,Timestamp.valueOf(created));});
      if(n.hasNonNull("couponCode")){String status="USED".equals(n.path("status").asText())?"USED":"EXPIRED".equals(n.path("status").asText())?"EXPIRED":"UNUSED";update(c,coupon,p->{p.setLong(1,id);p.setLong(2,n.path("merchantId").asLong());p.setString(3,n.path("couponCode").asText());p.setString(4,status);if("USED".equals(status))p.setTimestamp(5,Timestamp.valueOf(LocalDateTime.now()));else p.setNull(5,java.sql.Types.TIMESTAMP);});}
    }
    return users;
  }

  private void writeReviews(Connection c, JsonNode nodes, Map<Long,Long> orderUsers) throws SQLException {
    String sql="INSERT INTO review(id,order_id,user_id,merchant_id,user_name_snapshot,score,taste_score,service_score,content,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
    for(JsonNode n:nodes)update(c,sql,p->{LocalDateTime at=parseTime(n.path("createdAt"));p.setLong(1,n.path("id").asLong());p.setLong(2,n.path("orderId").asLong());p.setLong(3,orderUsers.get(n.path("orderId").asLong()));p.setLong(4,n.path("merchantId").asLong());p.setString(5,n.path("userName").asText());p.setInt(6,n.path("score").asInt());p.setInt(7,n.path("tasteScore").asInt());p.setInt(8,n.path("serviceScore").asInt());p.setString(9,n.path("content").asText());p.setTimestamp(10,Timestamp.valueOf(at));p.setTimestamp(11,Timestamp.valueOf(at));});
  }

  private void writeFavorites(Connection c, JsonNode favorites) {
    String sql="INSERT INTO merchant_favorite(user_id,merchant_id) VALUES(?,?)";
    favorites.fields().forEachRemaining(entry->{for(JsonNode id:entry.getValue())uncheckedUpdate(c,sql,p->{p.setLong(1,Long.parseLong(entry.getKey()));p.setLong(2,id.asLong());});});
  }

  private void writeConversations(Connection c, JsonNode groups) throws SQLException {
    String sql="INSERT INTO chat_message(id,user_id,merchant_id,sender_role,sender_name,content,created_at) VALUES(?,?,?,?,?,?,?)";
    for(JsonNode group:groups)for(JsonNode n:group.path("messages"))update(c,sql,p->{p.setLong(1,n.path("id").asLong());p.setLong(2,n.path("userId").asLong());p.setLong(3,n.path("merchantId").asLong());p.setString(4,toDatabaseSenderRole(n.path("senderRole").asText()));p.setString(5,n.path("senderName").asText());p.setString(6,n.path("content").asText());p.setTimestamp(7,Timestamp.valueOf(parseTime(n.path("createdAt"))));});
  }

  private void writeLogs(Connection c, JsonNode nodes) throws SQLException {
    String sql="INSERT INTO operation_log(id,actor,action,created_at) VALUES(?,?,?,?)";
    for(JsonNode n:nodes)update(c,sql,p->{p.setLong(1,n.path("id").asLong());p.setString(2,n.path("actor").asText());p.setString(3,n.path("action").asText());p.setTimestamp(4,Timestamp.valueOf(parseTime(n.path("createdAt"))));});
  }

  private Optional<String> legacySnapshot(Connection c) throws SQLException {
    if(scalarLong(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='business_state'")==0)return Optional.empty();
    try(PreparedStatement p=c.prepareStatement("SELECT payload FROM business_state WHERE state_key='primary'");ResultSet r=p.executeQuery()){return r.next()?Optional.of(r.getString(1)):Optional.empty();}
  }

  private Connection open() throws SQLException { return DriverManager.getConnection(url,username,password); }
  private boolean acquireLock(Connection c) throws SQLException { try(PreparedStatement p=c.prepareStatement("SELECT GET_LOCK(?,10)")){p.setString(1,LOCK_NAME);try(ResultSet r=p.executeQuery()){return r.next()&&r.getInt(1)==1;}} }
  private void releaseLock(Connection c){try(PreparedStatement p=c.prepareStatement("SELECT RELEASE_LOCK(?)")){p.setString(1,LOCK_NAME);p.executeQuery();}catch(SQLException ignored){}}
  private long scalarLong(Connection c,String sql)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){return r.next()?r.getLong(1):0;}}
  private void execute(Connection c,String sql)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql)){p.executeUpdate();}}
  private void query(Connection c,String sql,RowConsumer consumer)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){while(r.next())consumer.accept(r);}}
  private void update(Connection c,String sql,StatementBinder binder)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql)){binder.bind(p);p.executeUpdate();}}
  private void uncheckedUpdate(Connection c,String sql,StatementBinder binder){try{update(c,sql,binder);}catch(SQLException error){throw new JdbcWriteException(error);}}
  private static void setNullableLong(PreparedStatement p,int index,JsonNode n)throws SQLException{if(n==null||n.isNull())p.setNull(index,java.sql.Types.BIGINT);else p.setLong(index,n.asLong());}
  private static void setNullableString(PreparedStatement p,int index,JsonNode n)throws SQLException{if(n==null||n.isNull())p.setNull(index,java.sql.Types.VARCHAR);else p.setString(index,n.asText());}
  private static void putNullable(ObjectNode n,String field,String value){if(value==null)n.putNull(field);else n.put(field,value);}
  private static String toDatabaseSenderRole(String role){return "MERCHANT".equals(role)?"MERCHANT_ADMIN":"MERCHANT_AI".equals(role)?"ASSISTANT":role;}
  private static String fromDatabaseSenderRole(String role){return "MERCHANT_ADMIN".equals(role)?"MERCHANT":"ASSISTANT".equals(role)?"MERCHANT_AI":role;}
  private static LocalDateTime parseTime(JsonNode n){return n==null||n.isNull()||n.asText().isBlank()?LocalDateTime.now():LocalDateTime.parse(n.asText());}
  @FunctionalInterface private interface RowConsumer { void accept(ResultSet row)throws SQLException; }
  @FunctionalInterface private interface StatementBinder { void bind(PreparedStatement statement)throws SQLException; }
  private static final class JdbcWriteException extends RuntimeException { JdbcWriteException(SQLException cause){super(cause);} }
}
