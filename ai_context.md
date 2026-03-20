## 核心修改记录（AI Context）

该工程实现了如下游戏逻辑
棋子：有黑白两面，总共有18个。
棋盘：根据@cc文件中所有点组成，用直线连接可连通的点。一个点即为落子点。
棋子移规则：两个落子点之有直线连通就可以移动到目标位置

对弈规则：
1、先定义两个概念，
1.1 夹：三个棋子在一条直线上，并且三个棋子相邻，那么中间的棋子被两边的棋子夹。
1.2 挑：三个棋子在一条直线上，并且三个棋子相邻，那么两边的棋子被中间的棋子挑。
2、假设黑棋先手，移动后，如果存在黑棋夹、挑的情况，那么被夹、挑的白棋变成黑棋，完成夹、挑的动作后，根据当前棋牌的变动，递归进行黑棋的夹、挑逻辑。
3、结果判断，对弈方如果所有棋子变成0，那么判输。

### 1) 前端音效与背景音乐

- 新增大厅/房间两套背景音乐（循环播放）：
    - 大厅：`/sounds/bg-lobby.mp3`
    - 房间：`/sounds/bg-room.mp3`
- 背景音乐在进入/退出房间时自动切换。
- 增加浏览器自动播放兼容处理：首次用户交互后解锁音频。
- 新增“声音设置”弹窗（大厅和房间都可打开）：
    - 分别调节音效音量（SFX）和背景音乐音量（BGM）
    - 默认值：SFX 0.8、BGM 0.35
    - 音量持久化：`localStorage`（`bg_sfxVolume`、`bg_bgmVolume`）
    - 提供试听按钮。

### 2) 前端移动端适配与显示优化

- 增加移动端响应式布局（`max-width: 720px`）：
    - 房间内由左右布局切为上下布局，避免棋盘/信息面板溢出。
    - 头部按钮与大厅昵称区支持换行。
    - 记录面板在手机端改为全屏覆盖。
- 优化席位显示：
    - 对弈席位与观众席显示“（我）”标记。
    - 房间标题后显示问候文案：`您好，{用户名称}`。
    - 对弈席位窗口固定高度：`.players-window { height: 60px; }`（减少回放/棋盘缩放时信息面板抖动）。
- 兼容老浏览器：
    - `crypto.randomUUID()` 增加 fallback，避免 Ubuntu/Nginx 部署后部分终端报错。

### 3) 后端房间治理与管理员能力

- 新增“无棋手超时清房”机制：
    - 条件：房间黑白席位都为空。
    - 行为：10 分钟后若仍无棋手，踢出所有观众并重置房间到初始状态。
    - 若倒计时期间有棋手加入，则自动取消任务。
- 新增管理员重置接口：
    - `POST /api/rooms/{roomId}/admin-reset`
    - 昵称为 `hyuan` 的用户可在大厅触发“重置房间”。
    - 重置内容：踢出所有用户、关闭 SSE 连接、重置棋局/聊天/邀请并开始新记录。
    - 服务端通过 SSE 的 `close` 事件主动“收口”连接（并带上 `reason`），前端监听到后会自动退回大厅（见第 7 条）。

### 4) 后端日志能力

- 新增 REST 访问日志过滤器 `AccessLogFilter`：
    - 记录方法、路径、状态码、耗时、客户端 IP。
    - 按状态码分级：2xx=info，4xx=warn，5xx=error。
    - SSE 请求降噪：成功请求不打印详细日志，错误请求保留。

### 5) 资源与打包部署

- 将棋盘拓扑文件 `cc` 从项目根迁移至 `src/main/resources/cc`，随 jar 一起发布。
- `Board` 读取逻辑改为优先 classpath，兼容 fallback 到工作目录文件。
- `pom.xml`：
    - Java 目标版本调整为 11。
    - 显式配置 `spring-boot-maven-plugin` 并开启 `repackage`，生成可运行 fat-jar。
- 新增 `start.sh`：
    - 启动前检查后端(8080)/前端(5173)是否已运行，未运行才启动。
    - 日志写入 `logs/`，PID 写入 `.pids/`。

### 6) 前端棋盘像素对齐与 `room-mode` 布局

- 棋盘格子尺寸与连线像素对齐由 JS/HTML/CSS 协同控制：
    - `frontend/src/App.vue` 内的 `updateBoardMetrics()` 计算统一的 `{ cell, gap }`（并区分大厅/房间模式、移动端/桌面端）。
    - `boardStyle` 将 `{ cell, gap }` 写入 CSS 变量：`--cell` / `--gap`，从而驱动 `.board` 网格与 `.cell` 尺寸。
    - 同一组 `{ cell, gap }` 会用于计算 SVG 的 `viewBox`（`svgViewBox`），并渲染 `<svg class="board-lines" :viewBox="svgViewBox">`。
    - CSS 对 SVG 连线采用像素化 stroke：`.board-lines line { stroke-width: 1; }`，避免格子/连线在不同缩放下错位。

- `room-mode` 视口与对齐策略：
    - `.game-container.room-mode` 固定视口高度并 `overflow: hidden`，避免页面滚动导致布局漂移。
    - `.game-container.room-mode .board-wrapper` 使用 `height: calc(100% - 64px)`（扣除 header 区域），并显式 `align-items: flex-start`，防止上下拉伸带来的错位。

### 7) SSE `close` 自动退回大厅 & 观众“邀请替换”

- SSE `close` -> 自动退回大厅：
    - 前端 `frontend/src/App.vue` 在 `connectSse()` 中对 `EventSource` 监听 `close` 事件：`es.addEventListener('close', ...)`，解析 `evt.data.reason`
      ，调用 `forceExitRoomToLobbyByServer()`。
    - 触发 `exitRoomToLobby()`：断开 SSE、清理 `currentRoomId` 以及 `localStorage`（`bg_roomId` / `bg_mode`），并重置房间相关状态（如模态框/聊天等）。
    - 后端在“管理员重置/无棋手清理”等场景调用 `eventHub.closeRoom(roomId, reason)`，并对每个用户做关闭（`delayCloseUser`），从而触发 SSE 的 `close` 事件。

- 观众“邀请替换”流程（让观众可替换当前棋手席位）：
    - 房间内 `spectators-window` 对非棋手用户渲染按钮“邀请替换”，点击后调用 `inviteSpectator()` -> `createInvite(toUserId, seat)`。
    - `createInvite` 调用后端 `POST /api/rooms/{roomId}/invites`（`RoomService.createInvite`），创建 `Invite` 并通过 SSE 推送事件类型 `invite`。
    - 前端监听 SSE 的 `invite` 事件，弹出“替换邀请”模态框；用户点击同意/拒绝后执行 `respondInvite(action)`，请求 `POST /api/rooms/{roomId}/invites/{inviteId}/respond`。
    - 后端处理响应后推送 `inviteResolved`（前端当前主要是接收，不做复杂 UI 变化）。

### 8) 实时对局：夹/挑分步动画（非仅回放）

- 当一手棋触发递归夹/挑时，`GameInstance` 在 `captureSteps` 中记录：**落子后尚未翻转**的一帧 + **按规则分组的翻转帧**；`MoveResult` 另带 `captureAnimationInitialMs`
  / `captureAnimationStepMs`（与 `RoomService` 常量一致）。
    - `夹`：只翻转中间 1 枚 => 1 帧
    - `挑`：同时翻转两边 2 枚 => **同一帧**
- `RoomService.move` 成功落子后：若有 `captureSteps`，用 `captureAnimScheduler` **按延时多次广播 SSE `state`**（与 JSONL 多行 `move` 记录一致），最后再广播一手结束后的终局 `state`（换手/胜负）。
- 前端 `App.vue`：**走棋方**用 HTTP 返回的 `captureSteps` 调用 `playLiveCaptureAnimation` 本地逐帧播放；`liveCaptureAnimating` 为真时跳过对棋盘的 `applyState`，避免与分步 SSE
  重复写盘（`room` 仍会更新）。**观众**仅通过分步 SSE 看到翻转过程。

### 9) 局内重赛：重开一局 / 再开一局（原有功能）

- 对弈进行中（尚未决胜）：
    - 当前两个棋手可点击“重开一局”按钮（保留两名棋手席位）。
    - 点击后立即初始化棋牌并重新开始对弈。
    - 额外会广播 toast：`{用户}想要再来一局`。
- 决出胜负后：
    - 对弈双方任意一方点击“再开一局”按钮即立即开始新一局。
    - 同样广播 toast：`{用户}想要再来一局`（不需要对方同意）。
- UI 位置约定：
    - “重开/再开一局”按钮显示在“退出房间”按钮的左边。

- 后端实现：
    - 新增接口：`POST /api/rooms/{roomId}/rematch`
    - 逻辑：调用 `Room.resetGameToNewMatchPreservePlayers()` 保留 `blackPlayerUserId/whitePlayerUserId`，重置棋盘/胜负/记录文件，并向客户端广播新一局 `state`。
    - toast 广播：通过 SSE `rematchProposed` 事件携带 `proposerName`。
- 前端实现：
    - `frontend/src/App.vue` 顶部对局控制栏恢复按钮；根据 `winner` 切换文案（胜负未出显示“重开一局”，已出显示“再开一局”）。
    - 监听 SSE `rematchProposed`，显示 toast：`${proposerName}想要再来一局`。

---

## 来自 `ai_cotenxt.md` 的补充内容

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

---

## 最近新增功能补充

### 6) 自己与自己对弈模式（双边由同一用户操控）

- 后端在房间 `Room` 中新增 `selfPlayOwnerUserId`，通过 `selfPlayOwnerUserId != null` 判断是否启用“自己与自己对弈”。
- 新增接口 `POST /api/rooms/{roomId}/self-play`：
  - 只有房间内“第一个进入的用户”可开启/关闭。
  - 开启后：开启者绑定黑白双方；其他已在房间内用户被降级为观众；后续进入用户在请求 `PLAYER` 时也会自动变为 `SPECTATOR`。
- 落子校验调整：
  - 自己与自己对弈时，开启者在前端 UI 侧允许根据轮到的回合颜色点击任意一枚自己的棋子。
  - 修复了前端原先仍基于固定 `me.seat` 进行回合校验，导致白棋回合无法操作的问题（改用 `isMyTurn`）。

### 7) 房间内“再开一局 / 重开一局”

- 房间内新增“重开一局”交互：
  - 未分胜负前：先发起方发起重开请求，双方棋手都可以点击；对方会看到“确认重开一局”弹窗，确认后才真正重置棋盘并开始新一局。
  - 已分胜负后：不需要确认，点击即可直接开新局。
  - 每次重开后用提示告知“谁又开了一局游戏”。
- 后端新增/修改：
  - `Room`：增加 `restartPendingFromUserId` / `lastRoundStarterUserId` / `lastRoundStarterAt`。
  - `RoomService`：新增 `restartRound`、`cancelRestartRound`，内部复用 `startNewRoundLocked` 重置棋局与新录像快照。
  - `RoomController`：新增 `POST /api/rooms/{roomId}/restart`、`POST /api/rooms/{roomId}/restart-cancel`。
- 前端新增/修改：
  - `frontend/src/App.vue`：新增“再开一局”按钮、对方确认弹窗与 toast 提示“谁开始新一局”。

