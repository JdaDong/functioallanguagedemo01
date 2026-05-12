(ns ecom.domain.user
  "用户领域：注册 / token 生成 / token 校验。
   简化版：token = (str user-id \"-\" 随机 8 字节 hex)，存内存表。
   生产里这部分应该是 JWT + Redis；本 demo 不上。")

(defn- random-hex [n]
  (let [bs (byte-array n)]
    (.nextBytes (java.security.SecureRandom.) bs)
    (apply str (map #(format "%02x" %) bs))))

(defn fresh-store []
  {:users  (atom {})         ;; user-id → {:user-id :name :email :password-hash}
   :tokens (atom {})})       ;; token   → user-id

(defn- hash-password
  "MD5 教学用；生产应该用 bcrypt/argon2"
  [pw]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        bs (.digest md (.getBytes ^String pw "UTF-8"))]
    (apply str (map #(format "%02x" %) bs))))

(defn register!
  [{:keys [users] :as ustore} {:keys [user-id name email password]}]
  (cond
    (contains? @users user-id) {:error "user-id already exists"}
    (not (and (seq name) (seq email) (seq password)))
    {:error "name/email/password required"}
    :else
    (do (swap! users assoc user-id
               {:user-id        user-id
                :name           name
                :email          email
                :password-hash  (hash-password password)
                :created-at     (System/currentTimeMillis)})
        {:ok user-id})))

(defn login!
  [{:keys [users tokens]} {:keys [user-id password]}]
  (let [u (get @users user-id)]
    (cond
      (nil? u) {:error "no such user"}
      (not= (:password-hash u) (hash-password password))
      {:error "wrong password"}
      :else
      (let [tok (str user-id "-" (random-hex 8))]
        (swap! tokens assoc tok user-id)
        {:token tok :user-id user-id}))))

(defn whoami
  [{:keys [tokens users]} token]
  (when-let [uid (get @tokens token)]
    (get @users uid)))

(defn logout!
  [{:keys [tokens]} token]
  (swap! tokens dissoc token)
  :ok)
