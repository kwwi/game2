# 音效与资源替换说明

该项目的前端音效与棋子图片资源均做了“可替换/可配置”处理。默认情况下会从 `frontend/public/` 下加载资源。

---

## 一、音效文件（Sound Effects）

### 1. 默认音效路径与文件名

前端代码中使用的默认音效 URL 在 `frontend/src/App.vue`：

- `pickup`：`/sounds/pickup.mp3`（拿起棋子）
- `drop`：`/sounds/drop.mp3`（放下棋子/完成落子）
- `win`：`/sounds/win.mp3`（胜利方播放）
- `lose`：`/sounds/lose.mp3`（失败方播放）
- `end`：`/sounds/end.mp3`（对局结束音效：观众播放）
- `bgLobby`：`/sounds/bg-lobby.mp3`（大厅背景音乐：循环播放）
- `bgRoom`：`/sounds/bg-room.mp3`（房间背景音乐：循环播放）

因此你需要把音效文件放到：

`frontend/public/sounds/`

并使用如下文件名（与后缀保持一致，例如 mp3）：

- `pickup.mp3`
- `drop.mp3`
- `win.mp3`
- `lose.mp3`
- `end.mp3`
- `bg-lobby.mp3`
- `bg-room.mp3`

### 2. 替换方法

1. 将你自己的音效文件复制到 `frontend/public/sounds/`。
2. 保持文件名一致（推荐），或同时修改 `frontend/src/App.vue` 里的 `SOUND_URLS`。
3. 重新启动前端（或刷新页面）。

### 3. 格式说明

浏览器对音频格式支持取决于具体浏览器。当前默认使用的是 `.mp3`。

如果你使用其他格式（例如 `.ogg` / `.wav`），请：

- 同时修改 `frontend/src/App.vue` 里的对应 URL 后缀

### 4. 播放触发时机（与浏览器策略相关）

浏览器通常会限制“未触发用户交互时的自动播放”。本项目音效触发点包括：

- `pickup` / `drop`：由点击操作触发，通常可正常播放
- `win` / `lose` / `end`：在收到后端 `winner` 推送后触发，若浏览器仍拦截播放，可能需要用户先在页面上进行一次点击以“解锁音频”。
- `bgLobby` / `bgRoom`：背景音乐会尝试自动播放，但同样受浏览器“自动播放限制”影响。本项目会在用户第一次点击/触摸页面后解锁音频，并开始循环播放背景音乐；进入/退出房间会自动切换音乐轨道。

---

## 二、棋子图片资源（Pieces）

棋子原本使用 CSS 渐变作为兜底。现在也允许用图片替换棋子显示：

### 1. 默认棋子图片路径与文件名

CSS 中的默认图片 URL 在 `frontend/src/style.css`（`:root` 里）：

- `--piece-black-image`: `url('/pieces/piece-black.png')`
- `--piece-white-image`: `url('/pieces/piece-white.png')`

因此你需要把图片放到：

`frontend/public/pieces/`

并使用文件名：

- `piece-black.png`
- `piece-white.png`

### 2. 替换方法

1. 将你的黑白棋图片放入 `frontend/public/pieces/`，并保持上述文件名一致（推荐）。
2. 如果文件名不同，修改 `frontend/src/style.css` 中对应的 CSS 变量。
3. 刷新页面即可生效。

### 3. 画面效果

棋子图片会作为背景铺满棋子容器（`background-size: cover`），同时保留原有渐变作为兜底：即使图片不存在或加载失败，仍会显示渐变棋子。

---

## 三、你可能需要改的代码位置

- 音效文件名/路径：`frontend/src/App.vue` 中的 `SOUND_URLS`
- 棋子图片路径：`frontend/src/style.css` 中的 `--piece-black-image` / `--piece-white-image`

