## 本次核心修改点（AI Context）

### 前端：背景音乐与音量设置

- **新增大厅/房间两套背景音乐（循环播放）**
  - 大厅：`/sounds/bg-lobby.mp3`
  - 房间：`/sounds/bg-room.mp3`
  - 进入/退出房间自动切换播放曲目
  - 为兼容浏览器自动播放策略：首次用户点击/触摸页面后解锁音频并开始播放
- **新增声音设置入口（大厅与房间均可打开）**
  - 可分别调节 **音效音量（SFX）** 与 **背景音乐音量（BGM）**
  - 默认值：SFX = 0.8、BGM = 0.35
  - 使用 `localStorage` 持久化：`bg_sfxVolume` / `bg_bgmVolume`
  - 提供“试听音效/试听背景音乐”
- **资源目录与文档更新**
  - 建立 `frontend/public/sounds/` 与 `frontend/public/pieces/`（用 `.gitkeep` 保留目录）
  - 更新 `SOUND_EFFECTS.md`：补充 `bg-lobby.mp3` / `bg-room.mp3` 与播放策略说明

### 后端：记录与房间相关调整（本次提交中包含）

- **记录（JSONL）解析/加载继续对弈的健壮性提升**
  - 以 Jackson 逐行反序列化（先读 `type`，再映射到事件 DTO，如 `MoveEvent`），避免使用 `line.contains("\"type\":\"move\"")` 这类字符串匹配
- **DTO 化与接口返回结构优化**
  - 引入/完善 `src/main/java/com/example/boardgame/dto/` 相关 DTO（减少硬编码 key 的 `Map.of`/`of(...)` 风格返回）

### 构建：Java 编译目标版本调整

- **`pom.xml` 编译目标升级为 Java 11**
  - `<java.version>` 从 `1.8` 调整为 `11`
  - `maven-compiler-plugin` 的 `source/target` 统一跟随该属性

