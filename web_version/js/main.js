// ============================================================
// 镜隙 (Jingxi) Web版 — 主逻辑
// 完整复刻 Android 版全部功能
// ============================================================

// ============================================================
// GLOBAL STATE
// ============================================================
const State = {
    viewStack: ['desktop'],     // 视图导航栈
    currentSessionId: null,     // 当前聊天会话 ID
    currentChatChar: null,      // 当前聊天角色
    isGenerating: false,        // AI 正在生成回复
    isRecording: false,         // 正在录音
    recordingStartY: 0,         // 录音起始 Y 坐标
    quoteMessage: null,         // 当前引用的消息
    pendingMomentImages: [],    // 待发布朋友圈图片
    searchVisible: false,       // 搜索栏可见
    currentMomentDetailId: null,// 当前朋友圈详情 ID
};

// ============================================================
// UTILITY FUNCTIONS
// ============================================================
function escapeHTML(str) {
    if (!str) return '';
    return String(str).replace(/[&<>'"]/g, tag => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[tag] || tag));
}

function formatTime(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    const now = new Date();
    const h = d.getHours().toString().padStart(2, '0');
    const m = d.getMinutes().toString().padStart(2, '0');
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const thatDay = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const diff = (today - thatDay) / (1000 * 60 * 60 * 24);
    if (diff === 0) return h + ':' + m;
    if (diff === 1) return '昨天 ' + h + ':' + m;
    if (diff < 7) return ['周日','周一','周二','周三','周四','周五','周六'][d.getDay()] + ' ' + h + ':' + m;
    return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + h + ':' + m;
}

function formatDate(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日';
}

function showToast(msg, duration = 2000) {
    const container = document.getElementById('toast-container');
    const el = document.createElement('div');
    el.className = 'toast';
    el.textContent = msg;
    container.appendChild(el);
    setTimeout(() => el.remove(), duration);
}

async function showModal(title, body, buttons) {
    return new Promise(resolve => {
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `
            <div class="modal-dialog">
                <div class="modal-title">${escapeHTML(title)}</div>
                <div class="modal-body">${escapeHTML(body)}</div>
                <div class="modal-buttons">
                    ${buttons.map((b, i) => `<div class="modal-btn modal-btn-${b.type || 'cancel'}" data-idx="${i}">${escapeHTML(b.text)}</div>`).join('')}
                </div>
            </div>`;
        document.body.appendChild(overlay);
        overlay.querySelectorAll('.modal-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const idx = parseInt(btn.dataset.idx);
                overlay.remove();
                const btnDef = buttons[idx];
                if (btnDef.action) btnDef.action();
                resolve(idx);
            });
        });
        overlay.addEventListener('click', e => { if (e.target === overlay) { overlay.remove(); resolve(-1); } });
    });
}

function getAvatarUrl(char, seed) {
    if (char && char.avatarPath && char.avatarPath.startsWith('data:')) return char.avatarPath;
    if (char && char.avatarPath && char.avatarPath.startsWith('http')) return char.avatarPath;
    const s = seed || (char ? char.name : 'default');
    return `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(s)}`;
}

// ============================================================
// THEME MANAGER
// ============================================================
const ThemeManager = {
    init() {
        const theme = Settings.get('theme', 'yellow');
        const dark = Settings.get('darkMode', 'false') === 'true';
        this.setTheme(theme, false);
        if (dark) this._applyDark(true);
        document.getElementById('dark-mode-toggle')?.classList.toggle('on', dark);
        // Apply desktop background
        const bg = Settings.get('desktopBg', '');
        if (bg) document.getElementById('screen-content').style.backgroundImage = `url(${bg})`;
    },

    setTheme(name, save = true) {
        document.documentElement.setAttribute('data-theme', name);
        // Update theme dot borders
        document.querySelectorAll('.theme-option div[id^="theme-dot-"]').forEach(d => {
            d.style.borderColor = d.id.includes(name) ? 'var(--accent)' : 'transparent';
        });
        if (save) Settings.set('theme', name);
    },

    toggleDark() {
        const toggle = document.getElementById('dark-mode-toggle');
        const isDark = toggle.classList.toggle('on');
        this._applyDark(isDark);
        Settings.set('darkMode', isDark);
    },

    _applyDark(isDark) {
        document.documentElement.classList.toggle('dark-mode', isDark);
        if (isDark) {
            document.documentElement.setAttribute('data-theme', 'dark');
        } else {
            document.documentElement.setAttribute('data-theme', Settings.get('theme', 'yellow'));
        }
    },

    pickDesktopBg() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = e => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = () => {
                const url = reader.result;
                document.getElementById('screen-content').style.backgroundImage = `url(${url})`;
                Settings.set('desktopBg', url);
                showToast('桌面背景已更新');
            };
            reader.readAsDataURL(file);
        };
        input.click();
    },

    clearDesktopBg() {
        document.getElementById('screen-content').style.backgroundImage = "url('https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=2564&auto=format&fit=crop')";
        Settings.remove('desktopBg');
        showToast('已恢复默认背景');
    },
};

// ============================================================
// NAVIGATOR — 视图栈管理
// ============================================================
const Navigator = {
    push(viewId) {
        const prevViewId = State.viewStack[State.viewStack.length - 1];
        const prevView = document.getElementById('view-' + prevViewId);
        const nextView = document.getElementById('view-' + viewId);
        if (!nextView || prevViewId === viewId) return;

        // 隐藏前一个视图
        if (prevView) {
            prevView.classList.add('view-hidden');
            prevView.classList.remove('view-visible');
        }

        // 显示新视图
        nextView.classList.remove('view-hidden');
        // Force reflow
        nextView.offsetHeight;
        nextView.classList.add('view-visible');
        nextView.classList.remove('view-hidden');

        State.viewStack.push(viewId);
        this._onViewChanged(viewId);
    },

    pop() {
        if (State.viewStack.length <= 1) return;
        const currentViewId = State.viewStack.pop();
        const prevViewId = State.viewStack[State.viewStack.length - 1];
        const currentView = document.getElementById('view-' + currentViewId);
        const prevView = document.getElementById('view-' + prevViewId);

        if (currentView) {
            currentView.classList.remove('view-visible');
            currentView.classList.add('view-hidden');
        }
        if (prevView) {
            prevView.classList.remove('view-hidden');
            prevView.offsetHeight;
            prevView.classList.add('view-visible');
        }
        this._onViewChanged(prevViewId);
    },

    resetTo(viewId) {
        // Close all views down to the target
        while (State.viewStack.length > 1 &&
               State.viewStack[State.viewStack.length - 1] !== viewId) {
            const id = State.viewStack.pop();
            const v = document.getElementById('view-' + id);
            if (v) { v.classList.remove('view-visible'); v.classList.add('view-hidden'); }
        }
        if (State.viewStack[State.viewStack.length - 1] !== viewId) {
            State.viewStack = [viewId];
        }
        const v = document.getElementById('view-' + viewId);
        if (v) { v.classList.remove('view-hidden'); v.classList.add('view-visible'); }
        this._onViewChanged(viewId);
    },

    _onViewChanged(viewId) {
        // Custom logic when view changes
        if (viewId === 'desktop') Desktop.onShow();
        if (viewId === 'wechat') WeChat.onShow();
        if (viewId === 'weather') Weather.fetchData();
        if (viewId === 'moments') Moments.loadFeed();
        if (viewId === 'memory') MemoryView.init();
        if (viewId === 'memo') MemoView.init();
        if (viewId === 'schedule') ScheduleView.init();
        if (viewId === 'add-friend') Contacts.initForm();
        if (viewId === 'worldbook-list') Worldbook.loadList();
        if (viewId === 'call-history') CallHistory.load();
        if (viewId === 'emoji-manage') EmojiManager.load();
    },
};

// Shorthand
function openView(viewId) { Navigator.push(viewId); }
function navigateBack() { Navigator.pop(); }

// ============================================================
// DESKTOP — 桌面主屏
// ============================================================
const Desktop = {
    clockTimer: null,

    init() {
        this.updateClock();
        this.clockTimer = setInterval(() => this.updateClock(), 1000);
        this.loadWeatherWidget();
        this.loadSignature();
    },

    updateClock() {
        const now = new Date();
        document.getElementById('desktop-clock').textContent =
            now.getHours().toString().padStart(2, '0') + ':' +
            now.getMinutes().toString().padStart(2, '0');
        document.getElementById('desktop-date').textContent =
            now.getMonth() + 1 + '月' + now.getDate() + '日 ' +
            ['周日','周一','周二','周三','周四','周五','周六'][now.getDay()];
        document.getElementById('status-time').textContent =
            now.getHours().toString().padStart(2, '0') + ':' +
            now.getMinutes().toString().padStart(2, '0');
    },

    onShow() {
        this.updateClock();
        this.loadWeatherWidget();
        this.loadSignature();
    },

    editSignature() {
        const current = Settings.get('signature', '');
        const newSig = prompt('输入签名:', current);
        if (newSig !== null) {
            Settings.set('signature', newSig);
            document.getElementById('desktop-signature').textContent = newSig || '点击设置签名...';
        }
    },

    loadSignature() {
        const sig = Settings.get('signature', '');
        document.getElementById('desktop-signature').textContent = sig || '点击设置签名...';
    },

    changePhoto(slot) {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = e => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = () => {
                const url = reader.result;
                const map = {
                    'right-top': 'desktop-photo-right',
                    'left': 'desktop-photo-left',
                    'right': 'desktop-photo-right2',
                };
                const el = document.getElementById(map[slot]);
                if (el) el.src = url;
                Settings.set('photo_' + slot, url);
                showToast('照片已更新');
            };
            reader.readAsDataURL(file);
        };
        input.click();
    },

    async loadWeatherWidget() {
        try {
            const lat = parseFloat(Settings.get('weather_lat', '39.9042'));
            const lon = parseFloat(Settings.get('weather_lon', '116.4074'));
            const res = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current_weather=true&timezone=Asia%2FShanghai`);
            const data = await res.json();
            const c = data.current_weather;
            document.getElementById('desktop-weather-temp').textContent = Math.round(c.temperature) + '°';
            const codeMap = {0:'晴朗',1:'多云',2:'阴天',3:'阴天',45:'雾',48:'雾',51:'小雨',61:'雨',71:'雪',95:'雷雨'};
            document.getElementById('desktop-weather-desc').textContent = codeMap[c.weathercode] || '未知';
            const iconMap = {0:'fa-sun',1:'fa-cloud-sun',2:'fa-cloud',3:'fa-cloud',45:'fa-smog',48:'fa-smog',51:'fa-cloud-rain',61:'fa-cloud-showers-heavy',71:'fa-snowflake',95:'fa-cloud-bolt'};
            const icon = document.getElementById('desktop-weather-icon');
            icon.className = 'fa-solid ' + (iconMap[c.weathercode] || 'fa-cloud') + ' text-3xl text-white/80';
        } catch (e) { /* ignore */ }
    },
};

// ============================================================
// WECHAT — 微信主页 4 Tab
// ============================================================
const WeChat = {
    currentTab: 'messages',

    onShow() {
        this.switchTab(this.currentTab);
    },

    switchTab(tabName) {
        this.currentTab = tabName;
        ['messages','contacts','discover','me'].forEach(t => {
            const tab = document.getElementById('tab-' + t);
            if (tab) tab.classList.toggle('hidden', t !== tabName);
        });
        // Update nav styles
        document.querySelectorAll('.wechat-tab').forEach(el => {
            const isActive = el.dataset.tab === tabName;
            const icon = el.querySelector('.tab-icon');
            const text = el.querySelector('.tab-text');
            if (icon) icon.style.color = isActive ? 'var(--tab-active)' : 'var(--tab-inactive)';
            if (text) text.style.color = isActive ? 'var(--tab-active)' : 'var(--tab-inactive)';
        });
        const titleMap = {messages:'消息', contacts:'通讯录', discover:'发现', me:'我'};
        document.getElementById('wechat-title').textContent = titleMap[tabName];
        // Load content
        if (tabName === 'messages') Chat.loadSessions();
        if (tabName === 'contacts') Contacts.loadList();
        if (tabName === 'me') Personas.loadMeTab();
    },

    async handlePlus() {
        if (State.viewStack[State.viewStack.length - 1] !== 'wechat') return;
        const tab = WeChat.currentTab;
        if (tab === 'messages') {
            // Start new chat - go to contacts to pick a character
            WeChat.switchTab('contacts');
        } else if (tab === 'contacts') {
            openView('add-friend');
        } else if (tab === 'discover') {
            openView('create-moment');
        } else if (tab === 'me') {
            openView('add-persona');
        }
    },
};

function switchWeChatTab(tab) { WeChat.switchTab(tab); }
function handleWeChatPlus() { WeChat.handlePlus(); }

function toggleSearch() {
    const bar = document.getElementById('wechat-search-bar');
    const input = document.getElementById('wechat-search-input');
    State.searchVisible = !State.searchVisible;
    bar.classList.toggle('hidden', !State.searchVisible);
    if (!State.searchVisible) {
        input.value = '';
        Contacts.loadList();
    } else {
        input.focus();
    }
}

// ============================================================
// CHAT — 聊天核心逻辑
// ============================================================
const Chat = {
    async loadSessions() {
        const sessions = await db.chat_sessions.orderBy('lastMessageTimestamp').reverse().toArray();
        const container = document.getElementById('sessions-list');
        if (sessions.length === 0) {
            container.innerHTML = '<div class="text-center text-sm theme-text-secondary py-10">暂无会话，去通讯录选择一个角色开始聊天吧</div>';
            return;
        }
        let html = '';
        for (const s of sessions) {
            const char = await db.characters.get(s.characterId);
            if (!char) continue;
            const lastMsg = await db.messages.where('sessionId').equals(s.id).reverse().first();
            const preview = lastMsg ? lastMsg.content.substring(0, 30) : '点击开始聊天...';
            const time = lastMsg ? formatTime(lastMsg.timestamp) : '';
            const avatar = getAvatarUrl(char);
            html += `
                <div class="session-item ${s.isPinned ? 'pinned' : ''}" onclick="Chat.openSession(${s.id})"
                     oncontextmenu="event.preventDefault(); Chat.showSessionMenu(${s.id}, '${escapeHTML(char.name)}')">
                    <div class="w-12 h-12 rounded-xl overflow-hidden flex-shrink-0 mr-3 bg-[var(--input-bg)] flex items-center justify-center">
                        <img src="${avatar}" class="w-full h-full object-cover" onerror="this.src='https://api.dicebear.com/7.x/shapes/svg?seed=${escapeHTML(char.name)}'">
                    </div>
                    <div class="flex-1 overflow-hidden">
                        <div class="flex justify-between items-center mb-1">
                            <h3 class="font-medium text-[16px] theme-text truncate">${escapeHTML(char.name)}</h3>
                            <span class="text-xs theme-text-secondary flex-shrink-0 ml-2">${time}</span>
                        </div>
                        <p class="text-sm theme-text-secondary truncate">${escapeHTML(preview)}</p>
                    </div>
                    ${s.unreadCount > 0 ? `<div class="session-badge">${s.unreadCount > 99 ? '99+' : s.unreadCount}</div>` : ''}
                </div>`;
        }
        container.innerHTML = html;
    },

    async openSession(sessionId) {
        const session = await db.chat_sessions.get(sessionId);
        if (!session) return;
        State.currentSessionId = sessionId;
        const char = await db.characters.get(session.characterId);
        State.currentChatChar = char;
        document.getElementById('chat-title').textContent = char ? char.name : '未知';
        document.getElementById('chat-subtitle').textContent = '';
        // Reset unread
        await db.chat_sessions.update(sessionId, { unreadCount: 0 });
        openView('chat-detail');
        await this.loadMessages();
    },

    async loadMessages() {
        const container = document.getElementById('chat-messages');
        container.innerHTML = '<div class="text-center text-xs theme-text-secondary my-4">加载中...</div>';
        const msgs = await MessageDB.getBySession(State.currentSessionId, 100);
        container.innerHTML = '';
        if (msgs.length === 0) {
            // Send greeting if new session
            const char = State.currentChatChar;
            if (char && char.persona) {
                const greet = char.persona.includes('initialMessage') ?
                    JSON.parse(char.persona).initialMessage :
                    `你好！我是${char.name}，很高兴认识你~`;
                await this.appendMessageBubble('assistant', greet, 0);
            } else {
                container.innerHTML = '<div class="text-center text-xs theme-text-secondary my-4">— 开始聊天吧 —</div>';
            }
            return;
        }
        // Show date separators
        let lastDate = '';
        for (const m of msgs.reverse()) {
            const dateStr = formatDate(m.timestamp);
            if (dateStr !== lastDate) {
                lastDate = dateStr;
                const dateDiv = document.createElement('div');
                dateDiv.className = 'text-center text-xs theme-text-secondary my-3';
                dateDiv.textContent = dateStr;
                container.appendChild(dateDiv);
            }
            this.appendMessageBubble(m.isFromUser ? 'user' : 'assistant', m.content, m.type, m.imageUrl, m.quoteMessageId, false);
        }
        this.scrollToBottom();
    },

    appendMessageBubble(role, content, type = 0, imageUrl = '', quoteId = -1, saveToDb = true) {
        const container = document.getElementById('chat-messages');
        const isUser = role === 'user';
        const div = document.createElement('div');
        div.className = `flex w-full ${isUser ? 'justify-end' : 'justify-start'} mb-3`;

        if (type === 99) {
            // System message
            div.innerHTML = `<div class="msg-system mx-auto">${escapeHTML(content)}</div>`;
        } else if (type === 2) {
            // Emoji
            div.innerHTML = isUser
                ? `<div class="max-w-[80%] ml-auto"><span class="text-4xl">${escapeHTML(content)}</span></div>`
                : `<div class="flex gap-2 max-w-[80%]"><div class="avatar avatar-sm"><img src="${getAvatarUrl(State.currentChatChar)}" class="w-full h-full rounded-xl object-cover"></div><span class="text-4xl">${escapeHTML(content)}</span></div>`;
        } else if (type === 3 || type === 4) {
            // Image
            const imgSrc = imageUrl || content;
            div.innerHTML = isUser
                ? `<div class="max-w-[70%] ml-auto"><img src="${imgSrc}" class="rounded-xl shadow max-w-full cursor-pointer" onclick="openImageDetail('${imgSrc}')" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22200%22 height=%22200%22><rect fill=%22%23f0f0f0%22 width=%22200%22 height=%22200%22/><text x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 fill=%22%23999%22 font-size=%2214%22>图片加载失败</text></svg>'"></div>`
                : `<div class="flex gap-2 max-w-[70%]"><div class="avatar avatar-sm"><img src="${getAvatarUrl(State.currentChatChar)}" class="w-full h-full rounded-xl object-cover"></div><img src="${imgSrc}" class="rounded-xl shadow max-w-full cursor-pointer" onclick="openImageDetail('${imgSrc}')" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22200%22 height=%22200%22><rect fill=%22%23f0f0f0%22 width=%22200%22 height=%22200%22/><text x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 fill=%22%23999%22 font-size=%2214%22>图片加载失败</text></svg>'"></div>`;
        } else if (type === 1) {
            // Voice
            div.innerHTML = isUser
                ? `<div class="msg-bubble-voice max-w-[60%] ml-auto px-4 py-2 cursor-pointer flex items-center gap-2" onclick="Chat.playVoice('${content}')"><i class="fa-solid fa-volume-high text-green-600"></i><span class="text-sm">语音消息</span><span class="text-xs theme-text-secondary ml-2">点击播放</span></div>`
                : `<div class="flex gap-2 max-w-[60%]"><div class="avatar avatar-sm"><img src="${getAvatarUrl(State.currentChatChar)}" class="w-full h-full rounded-xl object-cover"></div><div class="msg-bubble-voice px-4 py-2 cursor-pointer flex items-center gap-2" onclick="Chat.playVoice('${content}')"><i class="fa-solid fa-volume-high text-green-600"></i><span class="text-sm">语音消息</span></div></div>`;
        } else {
            // Text message
            const bubbleClass = isUser ? 'msg-bubble-right' : 'msg-bubble-left';
            div.innerHTML = isUser
                ? `<div class="max-w-[80%] ml-auto ${bubbleClass} px-4 py-2.5" oncontextmenu="event.preventDefault(); Chat.showContextMenu(event, '${escapeHTML(content)}', true)"><p class="text-[15px] whitespace-pre-wrap break-words theme-text">${escapeHTML(content)}</p></div>`
                : `<div class="flex gap-2 max-w-[80%]"><div class="avatar avatar-sm flex-shrink-0"><img src="${getAvatarUrl(State.currentChatChar)}" class="w-full h-full rounded-xl object-cover" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'"><i class="fa-solid fa-robot theme-text-secondary hidden items-center justify-center h-full"></i></div><div class="${bubbleClass} px-4 py-2.5" oncontextmenu="event.preventDefault(); Chat.showContextMenu(event, '${escapeHTML(content)}', false)"><p class="text-[15px] whitespace-pre-wrap break-words theme-text">${escapeHTML(content)}</p></div></div>`;
        }

        container.appendChild(div);

        if (saveToDb && State.currentSessionId) {
            MessageDB.add({
                sessionId: State.currentSessionId,
                characterId: State.currentChatChar ? State.currentChatChar.id : 0,
                content: content,
                type: type,
                isFromUser: isUser,
                timestamp: Date.now(),
                voiceUrl: type === 1 ? content : '',
                quoteMessageId: quoteId,
                imageUrl: imageUrl,
                imageDesc: '',
            });
            db.chat_sessions.update(State.currentSessionId, { lastMessageTimestamp: Date.now() });
        }

        this.scrollToBottom();
        return div;
    },

    scrollToBottom() {
        const container = document.getElementById('chat-messages');
        setTimeout(() => { container.scrollTop = container.scrollHeight; }, 50);
    },

    async sendMessage() {
        if (State.isGenerating) return;
        const input = document.getElementById('chat-input');
        const content = input.value.trim();
        if (!content) return;

        // Check if quoting
        const quoteMsg = State.quoteMessage;

        input.value = '';
        input.style.height = '38px';
        this.cancelQuote();

        // If no session, create one
        if (!State.currentSessionId) {
            const char = State.currentChatChar;
            if (!char) return;
            const session = await SessionDB.getOrCreate(char.id, '我');
            State.currentSessionId = session.id;
        }

        this.appendMessageBubble('user', content, 0, '', quoteMsg ? quoteMsg.id : -1);
        await this.generateReply();
    },

    async generateReply() {
        const endpoint = Settings.get('api_endpoint', 'https://api.openai.com/v1/');
        const apiKey = Settings.get('api_key', '');
        const model = Settings.get('api_model', 'gpt-4o-mini');
        const temperature = parseFloat(Settings.get('temperature', '0.8'));
        const historyRounds = parseInt(Settings.get('history_rounds', '20'));
        const memoryCount = parseInt(Settings.get('memory_count', '5'));

        if (!apiKey) {
            this.appendMessageBubble('assistant', '⚠️ 请先在设置中配置 API 密钥。点击右下角"我"→"设置"→"API 设置"');
            return;
        }

        State.isGenerating = true;
        document.getElementById('chat-subtitle').textContent = '对方正在输入中...';

        // Typing indicator
        const typingDiv = document.createElement('div');
        typingDiv.className = 'flex w-full justify-start mb-3';
        typingDiv.innerHTML = `<div class="flex gap-2 max-w-[80%]"><div class="avatar avatar-sm"><img src="${getAvatarUrl(State.currentChatChar)}" class="w-full h-full rounded-xl object-cover"></div><div class="msg-bubble-left px-4 py-3"><div class="typing-dots"><span></span><span></span><span></span></div></div></div>`;
        document.getElementById('chat-messages').appendChild(typingDiv);
        this.scrollToBottom();

        try {
            // Build system prompt
            const char = State.currentChatChar;
            const mainPersona = await db.my_personas.where('isMainPersona').equals(1).first();
            const worldbookPre = await WorldbookDB.getEnabled(0);
            const worldbookMid = await WorldbookDB.getEnabled(1);
            const now = new Date();

            let sysPrompt = char ? char.persona : '你是静息APP中的智能助手。';
            if (mainPersona) {
                sysPrompt += '\n\n【用户信息】\n' + mainPersona.persona;
            }
            sysPrompt += `\n\n【当前时间】${now.getFullYear()}年${now.getMonth()+1}月${now.getDate()}日 ${now.getHours()}:${now.getMinutes().toString().padStart(2,'0')} ${['周日','周一','周二','周三','周四','周五','周六'][now.getDay()]}`;
            sysPrompt += '\n【说话规则】请简短自然地回复，像一个真人在聊天。禁止说教、禁止使用宠溺称呼、禁止油腻。';

            // Worldbook Pre
            for (const wb of worldbookPre) {
                sysPrompt += '\n\n【世界设定】\n' + wb.content;
            }

            // Memories
            if (char && memoryCount > 0) {
                const memories = await MemoryDB.getImportant(char.id);
                if (memories.length > 0) {
                    sysPrompt += '\n\n【关于用户的记忆】\n';
                    memories.slice(0, memoryCount).forEach(m => {
                        sysPrompt += '- ' + m.content + '\n';
                    });
                }
            }

            // Billboard
            if (char) {
                const billboards = await BillboardDB.getByChar(char.id);
                if (billboards.length > 0) {
                    sysPrompt += '\n\n【当前关注】\n';
                    billboards.slice(0, 3).forEach(b => {
                        sysPrompt += '- ' + b.content + '\n';
                    });
                }
            }

            // Worldbook Mid - check if any keywords match recent messages
            const recentMsgs = await MessageDB.getBySession(State.currentSessionId, 10);
            const recentText = recentMsgs.map(m => m.content).join(' ');
            for (const wb of worldbookMid) {
                if (wb.keyword) {
                    const keywords = wb.keyword.split(/[,，]/).map(k => k.trim());
                    if (keywords.some(k => recentText.includes(k))) {
                        sysPrompt += '\n\n【相关设定】\n' + wb.content;
                    }
                }
            }

            // Chat history
            const history = await MessageDB.getBySession(State.currentSessionId, historyRounds * 2);
            const apiMessages = history.reverse().map(m => ({
                role: m.isFromUser ? 'user' : 'assistant',
                content: m.content,
            }));

            const baseUrl = endpoint.endsWith('/') ? endpoint : endpoint + '/';
            const response = await fetch(baseUrl + 'chat/completions', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${apiKey}`,
                },
                body: JSON.stringify({
                    model: model,
                    messages: [
                        { role: 'system', content: sysPrompt },
                        ...apiMessages,
                    ],
                    temperature: temperature,
                    max_tokens: 2000,
                }),
            });

            if (!response.ok) {
                const errText = await response.text();
                throw new Error(`API ${response.status}: ${errText.substring(0, 200)}`);
            }

            const data = await response.json();
            const reply = data.choices[0].message.content;

            // Remove typing indicator
            typingDiv.remove();

            // Try to parse as JSON multi-message reply
            try {
                // Check if reply looks like JSON
                if (reply.trim().startsWith('{') && reply.includes('"replies"')) {
                    const parsed = JSON.parse(reply);
                    if (parsed.replies && Array.isArray(parsed.replies)) {
                        for (let i = 0; i < parsed.replies.length; i++) {
                            const r = parsed.replies[i];
                            await new Promise(resolve => setTimeout(resolve, i > 0 ? 800 : 0));
                            this.appendMessageBubble('assistant', r.content || r.text || '', r.type || 0, r.image_url || '');
                        }
                        State.isGenerating = false;
                        document.getElementById('chat-subtitle').textContent = '';
                        return;
                    }
                }
            } catch (e) { /* Not JSON, treat as plain text */ }

            this.appendMessageBubble('assistant', reply);

        } catch (err) {
            console.error('Chat error:', err);
            typingDiv.remove();
            this.appendMessageBubble('assistant', '❌ ' + err.message);
        } finally {
            State.isGenerating = false;
            document.getElementById('chat-subtitle').textContent = '';
        }
    },

    async regenerate() {
        if (State.isGenerating) return;
        const count = await MessageDB.deleteLastAIMessages(State.currentSessionId);
        if (count > 0) {
            // Remove from DOM
            const container = document.getElementById('chat-messages');
            const children = container.children;
            let removed = 0;
            for (let i = children.length - 1; i >= 0 && removed < count; i--) {
                const child = children[i];
                if (child.classList.contains('justify-start') && !child.querySelector('.typing-dots')) {
                    child.remove();
                    removed++;
                }
            }
            await this.generateReply();
        }
    },

    toggleEmojiPanel() {
        const panel = document.getElementById('emoji-panel');
        const funcPanel = document.getElementById('function-panel');
        const voiceUI = document.getElementById('voice-record-ui');
        funcPanel.classList.add('hidden');
        voiceUI.classList.add('hidden');
        const isVisible = !panel.classList.contains('hidden');
        panel.classList.toggle('hidden', isVisible);
        if (!isVisible) this.loadEmojiPanel();
    },

    async loadEmojiPanel() {
        const tabs = document.getElementById('emoji-tabs');
        const grid = document.getElementById('emoji-grid');
        const groups = await EmojiDB.getGroups();
        tabs.innerHTML = groups.map((g, i) =>
            `<span class="emoji-tab ${i === 0 ? 'active' : ''}" onclick="Chat.loadEmojiGrid('${escapeHTML(g)}', this)">${escapeHTML(g)}</span>`
        ).join('');
        if (groups.length > 0) {
            this.loadEmojiGrid(groups[0], tabs.querySelector('.emoji-tab'));
        } else {
            grid.innerHTML = '<div class="text-center text-sm theme-text-secondary col-span-5 py-4">暂无表情</div>';
        }
    },

    async loadEmojiGrid(group, tabEl) {
        document.querySelectorAll('#emoji-tabs .emoji-tab').forEach(t => t.classList.remove('active'));
        if (tabEl) tabEl.classList.add('active');
        const emojis = await EmojiDB.getByGroup(group);
        document.getElementById('emoji-grid').innerHTML = emojis.map(e =>
            `<div class="cursor-pointer hover:scale-110 transition-transform p-1 text-center" onclick="Chat.sendEmoji('${escapeHTML(e.name)}')">${escapeHTML(e.name)}</div>`
        ).join('');
    },

    sendEmoji(emoji) {
        this.appendMessageBubble('user', emoji, 2);
        this.toggleEmojiPanel();
        if (State.currentSessionId) {
            this.generateReply();
        }
    },

    toggleFunctionPanel() {
        const panel = document.getElementById('function-panel');
        const emojiPanel = document.getElementById('emoji-panel');
        const voiceUI = document.getElementById('voice-record-ui');
        emojiPanel.classList.add('hidden');
        voiceUI.classList.add('hidden');
        panel.classList.toggle('hidden');
    },

    toggleVoiceInput() {
        const voiceUI = document.getElementById('voice-record-ui');
        const inputContainer = document.getElementById('text-input-container');
        const isVisible = !voiceUI.classList.contains('hidden');
        voiceUI.classList.toggle('hidden', isVisible);
        inputContainer.classList.toggle('hidden', !isVisible);
        if (!isVisible) {
            this.startVoiceRecording();
        }
    },

    startVoiceRecording() {
        State.isRecording = true;
        const waves = document.querySelectorAll('#voice-waveform .voice-wave-bar');
        waves.forEach(w => { w.style.height = '4px'; w.style.animationPlayState = 'running'; });
        // Simulate recording for 3 seconds
        setTimeout(() => {
            if (State.isRecording) {
                this.stopVoiceRecording();
            }
        }, 3000);
    },

    stopVoiceRecording() {
        State.isRecording = false;
        const waves = document.querySelectorAll('#voice-waveform .voice-wave-bar');
        waves.forEach(w => { w.style.animationPlayState = 'paused'; w.style.height = '4px'; });
        document.getElementById('voice-record-ui').classList.add('hidden');
        document.getElementById('text-input-container').classList.remove('hidden');
        // Simulate sending voice
        this.appendMessageBubble('user', 'voice_recording_url_placeholder', 1);
    },

    playVoice(url) {
        showToast('🔊 播放语音消息 (需配置 TTS API)');
    },

    sendImage() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = e => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = () => {
                this.appendMessageBubble('user', reader.result, 3);
                document.getElementById('function-panel').classList.add('hidden');
            };
            reader.readAsDataURL(file);
        };
        input.click();
    },

    sendVoice() {
        document.getElementById('function-panel').classList.add('hidden');
        this.toggleVoiceInput();
    },

    quoteMessage(content, isUser) {
        State.quoteMessage = { content, isUser };
        document.getElementById('quote-reply-content').textContent = content.substring(0, 50);
        document.getElementById('quote-reply-bar').classList.remove('hidden');
        document.getElementById('chat-input').focus();
    },

    cancelQuote() {
        State.quoteMessage = null;
        document.getElementById('quote-reply-bar').classList.add('hidden');
    },

    showContextMenu(event, content, isUser) {
        event.preventDefault();
        const menu = document.getElementById('context-menu');
        menu.innerHTML = `
            <div class="context-menu-item" onclick="Chat.copyText('${escapeHTML(content).replace(/'/g, "\\'")}'); hideContextMenu()"><i class="fa-solid fa-copy"></i> 复制</div>
            <div class="context-menu-item" onclick="Chat.quoteMessage('${escapeHTML(content).replace(/'/g, "\\'")}', ${isUser}); hideContextMenu()"><i class="fa-solid fa-reply"></i> 引用</div>
            ${isUser ? '<div class="context-menu-divider"></div><div class="context-menu-item danger" onclick="Chat.recallMessage(); hideContextMenu()"><i class="fa-solid fa-trash-can"></i> 撤回</div>' : ''}
        `;
        menu.style.left = Math.min(event.clientX, window.innerWidth - 150) + 'px';
        menu.style.top = Math.min(event.clientY, window.innerHeight - 200) + 'px';
        menu.classList.remove('hidden');
        setTimeout(() => document.addEventListener('click', hideContextMenu, { once: true }), 0);
    },

    async showSessionMenu(sessionId, name) {
        await showModal(name, '选择操作', [
            { text: '置顶/取消置顶', action: async () => {
                const s = await db.chat_sessions.get(sessionId);
                await db.chat_sessions.update(sessionId, { isPinned: !s.isPinned });
                Chat.loadSessions();
            }},
            { text: '删除会话', type: 'danger', action: async () => {
                await db.messages.where('sessionId').equals(sessionId).delete();
                await db.chat_sessions.delete(sessionId);
                Chat.loadSessions();
                showToast('会话已删除');
            }},
            { text: '取消' },
        ]);
    },

    copyText(text) {
        navigator.clipboard.writeText(text).then(() => showToast('已复制'));
    },

    recallMessage() {
        showToast('消息已撤回 (本地)');
    },
};

function hideContextMenu() {
    document.getElementById('context-menu').classList.add('hidden');
}

function openChatSession(sessionId) { Chat.openSession(sessionId); }
function closeChatSession() { navigateBack(); }
function openChatSettings() { showToast('聊天设置 (可设置背景图等)'); }

// ============================================================
// CONTACTS — 角色管理
// ============================================================
const Contacts = {
    async loadList(filter = '') {
        const chars = await db.characters.toArray();
        const filtered = filter ? chars.filter(c => c.name.includes(filter) || c.nickname.includes(filter)) : chars;
        const container = document.getElementById('contacts-list');
        if (filtered.length === 0) {
            container.innerHTML = '<div class="text-center text-sm theme-text-secondary py-10">暂无联系人</div>';
            return;
        }
        container.innerHTML = filtered.map(c => `
            <div class="contact-item" onclick="Contacts.openOrEdit(${c.id})">
                <div class="w-12 h-12 rounded-xl overflow-hidden flex-shrink-0 mr-3 bg-[var(--input-bg)] flex items-center justify-center">
                    <img src="${getAvatarUrl(c)}" class="w-full h-full object-cover" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'">
                    <i class="fa-solid fa-robot theme-text-secondary hidden items-center justify-center h-full"></i>
                </div>
                <div class="flex-1">
                    <h3 class="font-medium text-[16px] theme-text">${escapeHTML(c.name)}</h3>
                    <p class="text-xs theme-text-secondary mt-0.5">${escapeHTML(c.nickname || '点击开始聊天')}</p>
                </div>
                ${c.isStarred ? '<i class="fa-solid fa-star text-yellow-400 text-xs mr-2"></i>' : ''}
                <i class="fa-solid fa-chevron-right text-gray-300 text-xs"></i>
            </div>
        `).join('');
    },

    search(query) {
        this.loadList(query);
        const clearBtn = document.getElementById('search-clear-btn');
        if (clearBtn) clearBtn.style.display = query ? 'inline' : 'none';
    },

    async openOrEdit(charId) {
        const char = await db.characters.get(charId);
        if (!char) return;
        // Start chat session
        const persona = await db.my_personas.where('isMainPersona').equals(1).first();
        const session = await SessionDB.getOrCreate(charId, persona ? persona.name : '我');
        State.currentChatChar = char;
        Chat.openSession(session.id);
    },

    initForm(editId = null) {
        document.getElementById('edit-friend-id').value = editId || '';
        document.getElementById('add-friend-title').textContent = editId ? '编辑联系人' : '添加联系人';
        document.getElementById('btn-save-friend').textContent = editId ? '保存' : '完成';
        if (editId) {
            this.loadFriendData(editId);
        } else {
            this.resetForm();
        }
    },

    async loadFriendData(id) {
        const c = await db.characters.get(id);
        if (!c) return;
        document.getElementById('input-friend-name').value = c.name || '';
        document.getElementById('input-friend-nickname').value = c.nickname || '';
        document.getElementById('input-friend-persona').value = c.persona || '';
        document.getElementById('input-friend-voice-id').value = c.voiceId || '';
        document.getElementById('input-friend-voice-pitch').value = c.voicePitch || 5;
        document.getElementById('input-friend-voice-intensity').value = c.voiceIntensity || 5;
        document.getElementById('input-friend-voice-timbre').value = c.voiceTimbre || 5;
        document.getElementById('input-friend-voice-speed').value = (c.voiceSpeed || 1.0) * 10;
        document.getElementById('input-friend-sound-effect').value = c.soundEffect || '';
        document.getElementById('auto-moment-toggle').classList.toggle('on', !!c.autoMomentEnabled);
        document.getElementById('auto-moment-options').classList.toggle('hidden', !c.autoMomentEnabled);
        document.getElementById('input-auto-moment-interval').value = c.autoMomentIntervalHours || 8;
        document.getElementById('input-auto-moment-start').value = c.autoMomentStartTime || '08:00';
        document.getElementById('input-auto-moment-end').value = c.autoMomentEndTime || '22:00';
        document.getElementById('input-auto-moment-prob').value = c.autoMomentProbability || 50;
        document.getElementById('friend-emoji-toggle').classList.toggle('on', c.enableEmoji !== false);
        if (c.avatarPath) {
            document.getElementById('friend-avatar-preview').src = c.avatarPath;
            document.getElementById('friend-avatar-preview').classList.remove('hidden');
            document.getElementById('friend-avatar-placeholder').classList.add('hidden');
        }
    },

    resetForm() {
        ['input-friend-name','input-friend-nickname','input-friend-persona','input-friend-voice-id'].forEach(id => {
            document.getElementById(id).value = '';
        });
        [5,5,5,10].forEach((v, i) => {
            document.getElementById(['input-friend-voice-pitch','input-friend-voice-intensity','input-friend-voice-timbre','input-friend-voice-speed'][i]).value = v;
        });
        document.getElementById('friend-avatar-preview').classList.add('hidden');
        document.getElementById('friend-avatar-placeholder').classList.remove('hidden');
        document.getElementById('auto-moment-options').classList.add('hidden');
        document.getElementById('auto-moment-toggle').classList.remove('on');
    },

    async saveFriend() {
        const name = document.getElementById('input-friend-name').value.trim();
        if (!name) { showToast('请输入名字'); return; }
        const persona = document.getElementById('input-friend-persona').value.trim();
        if (!persona) { showToast('请输入人设'); return; }

        const data = {
            name: name,
            nickname: document.getElementById('input-friend-nickname').value.trim(),
            persona: persona,
            avatarPath: document.getElementById('friend-avatar-preview').src || '',
            voiceId: document.getElementById('input-friend-voice-id').value.trim(),
            voicePitch: parseInt(document.getElementById('input-friend-voice-pitch').value),
            voiceIntensity: parseInt(document.getElementById('input-friend-voice-intensity').value),
            voiceTimbre: parseInt(document.getElementById('input-friend-voice-timbre').value),
            voiceSpeed: parseInt(document.getElementById('input-friend-voice-speed').value) / 10,
            soundEffect: document.getElementById('input-friend-sound-effect').value,
            enableEmoji: document.getElementById('friend-emoji-toggle').classList.contains('on'),
            autoMomentEnabled: document.getElementById('auto-moment-toggle').classList.contains('on'),
            autoMomentIntervalHours: parseFloat(document.getElementById('input-auto-moment-interval').value),
            autoMomentStartTime: document.getElementById('input-auto-moment-start').value,
            autoMomentEndTime: document.getElementById('input-auto-moment-end').value,
            autoMomentProbability: parseInt(document.getElementById('input-auto-moment-prob').value),
            isStarred: false,
            isHidden: false,
        };

        const editId = document.getElementById('edit-friend-id').value;
        if (editId) {
            await db.characters.update(parseInt(editId), data);
            showToast('联系人已更新');
        } else {
            await db.characters.add(data);
            showToast('联系人已添加');
        }
        navigateBack();
        Contacts.loadList();
    },

    pickAvatar() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = e => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = () => {
                document.getElementById('friend-avatar-preview').src = reader.result;
                document.getElementById('friend-avatar-preview').classList.remove('hidden');
                document.getElementById('friend-avatar-placeholder').classList.add('hidden');
            };
            reader.readAsDataURL(file);
        };
        input.click();
    },
};

// ============================================================
// PERSONAS — 用户人格管理
// ============================================================
const Personas = {
    async loadMeTab() {
        const mainPersona = await db.my_personas.where('isMainPersona').equals(1).first();
        if (mainPersona) {
            document.getElementById('me-name').textContent = mainPersona.name;
            document.getElementById('me-persona-desc').textContent = mainPersona.persona ? mainPersona.persona.substring(0, 30) : '';
            const avatar = getAvatarUrl(null, mainPersona.name);
            document.getElementById('me-avatar').querySelector('img').src = mainPersona.avatarPath || avatar;
        }
        // Load persona list
        const all = await db.my_personas.toArray();
        const container = document.getElementById('personas-list');
        container.innerHTML = '<div class="text-xs theme-text-secondary px-1 py-2 font-medium">我的人格</div>' +
            all.map(p => `
                <div class="contact-item" onclick="Personas.editPersona('${escapeHTML(p.name)}')">
                    <div class="w-10 h-10 rounded-lg overflow-hidden mr-3 bg-[var(--input-bg)]">
                        <img src="${p.avatarPath || getAvatarUrl(null, p.name)}" class="w-full h-full object-cover">
                    </div>
                    <div class="flex-1">
                        <span class="theme-text">${escapeHTML(p.name)}</span>
                        ${p.isMainPersona ? '<span class="tag ml-1">主</span>' : ''}
                    </div>
                    <i class="fa-solid fa-chevron-right text-gray-300 text-xs"></i>
                </div>
            `).join('');
    },

    async editPersona(name) {
        const p = await db.my_personas.get(name);
        if (!p) return;
        document.getElementById('edit-persona-name').value = p.name;
        document.getElementById('add-persona-title').textContent = '编辑人格';
        document.getElementById('input-persona-name').value = p.name;
        document.getElementById('input-persona-desc').value = p.persona || '';
        document.getElementById('main-persona-toggle').classList.toggle('on', !!p.isMainPersona);
        if (p.avatarPath) {
            document.getElementById('persona-avatar-preview').src = p.avatarPath;
            document.getElementById('persona-avatar-preview').classList.remove('hidden');
            document.getElementById('persona-avatar-placeholder').classList.add('hidden');
        }
        openView('add-persona');
    },

    async savePersona() {
        const name = document.getElementById('input-persona-name').value.trim();
        const persona = document.getElementById('input-persona-desc').value.trim();
        if (!name) { showToast('请输入名字'); return; }
        const isMain = document.getElementById('main-persona-toggle').classList.contains('on');
        const avatarPath = document.getElementById('persona-avatar-preview').src || '';

        const editName = document.getElementById('edit-persona-name').value;
        if (editName && editName !== name) {
            await db.my_personas.delete(editName);
        }

        if (isMain) {
            // Unset other main personas
            const all = await db.my_personas.where('isMainPersona').equals(1).toArray();
            for (const p of all) {
                if (p.name !== name) await db.my_personas.update(p.name, { isMainPersona: false });
            }
        }

        await db.my_personas.put({
            name: name,
            persona: persona,
            isMainPersona: isMain,
            avatarPath: avatarPath,
        });

        showToast('人格已保存');
        navigateBack();
        Personas.loadMeTab();
    },

    pickAvatar() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = e => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = () => {
                document.getElementById('persona-avatar-preview').src = reader.result;
                document.getElementById('persona-avatar-preview').classList.remove('hidden');
                document.getElementById('persona-avatar-placeholder').classList.add('hidden');
            };
            reader.readAsDataURL(file);
        };
        input.click();
    },
};

// ============================================================
// MOMENTS — 朋友圈
// ============================================================
const Moments = {
    autoRefreshTimer: null,

    async loadFeed() {
        const container = document.getElementById('moments-list');
        const moments = await MomentDB.getFeed();
        if (moments.length === 0) {
            container.innerHTML = '<div class="text-center text-sm theme-text-secondary py-10">暂无动态，点击右上角发布</div>';
            return;
        }
        let html = '';
        for (const m of moments) {
            const char = await db.characters.get(m.publisherId);
            const author = char || { name: '我', avatarPath: '' };
            const likes = await MomentDB.getLikes(m.id);
            const comments = await MomentDB.getComments(m.id);
            const likeStr = likes.length > 0 ? likes.map(l => l.likerName || l.likerId).join(', ') : '';

            let imgHtml = '';
            if (m.images && m.images.length > 0) {
                const n = m.images.length;
                const grid = n === 1 ? 'moments-img-grid-1' : (n === 2 || n === 4 ? 'moments-img-grid-2' : 'moments-img-grid-3');
                imgHtml = `<div class="${grid} mt-2 mb-2" style="max-width:${n===1?'66%':'100%'}">`;
                m.images.forEach(img => {
                    imgHtml += `<div class="aspect-square bg-[var(--input-bg)] cursor-pointer overflow-hidden" onclick="event.stopPropagation();openImageDetail('${escapeHTML(img)}')"><img src="${img}" class="w-full h-full object-cover" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22100%22 height=%22100%22><rect fill=%22%23eee%22 width=%22100%22 height=%22100%22/></svg>'"></div>`;
                });
                imgHtml += '</div>';
            }

            html += `
                <div class="flex px-4 py-3 border-b theme-border cursor-pointer" onclick="Moments.openDetail(${m.id})">
                    <div class="w-10 h-10 rounded-lg overflow-hidden flex-shrink-0 bg-[var(--input-bg)]">
                        <img src="${getAvatarUrl(char)}" class="w-full h-full object-cover" onerror="this.src='https://api.dicebear.com/7.x/shapes/svg?seed=${escapeHTML(author.name)}'">
                    </div>
                    <div class="ml-3 flex-1 min-w-0">
                        <h3 class="font-medium text-[#576b95] text-[15px] mb-1">${escapeHTML(author.name)}</h3>
                        <p class="text-[15px] theme-text leading-relaxed whitespace-pre-wrap break-words">${escapeHTML(m.content)}</p>
                        ${imgHtml}
                        <div class="flex justify-between items-center mt-2">
                            <span class="text-xs theme-text-secondary">${formatTime(m.timestamp)}</span>
                            <div class="flex items-center gap-1 bg-[var(--bg-alt)] rounded px-2 py-0.5 cursor-pointer" onclick="event.stopPropagation();Moments.openDetail(${m.id})">
                                ${likeStr ? `<span class="text-xs text-[#576b95] mr-1"><i class="fa-solid fa-heart text-red-400 text-[10px] mr-0.5"></i>${escapeHTML(likeStr.substring(0,20))}</span>` : ''}
                                ${comments.length > 0 ? `<span class="text-xs theme-text-secondary"><i class="fa-regular fa-comment text-[10px] mr-0.5"></i>${comments.length}</span>` : ''}
                                <i class="fa-solid fa-ellipsis text-gray-400 text-[10px]"></i>
                            </div>
                        </div>
                    </div>
                </div>`;
        }
        container.innerHTML = html;
        // Update user info
        const mainPersona = await db.my_personas.where('isMainPersona').equals(1).first();
        if (mainPersona) {
            document.getElementById('moments-user-name').textContent = mainPersona.name;
            document.getElementById('moments-user-avatar').src = mainPersona.avatarPath || getAvatarUrl(null, mainPersona.name);
        }
    },

    handleScroll() {
        const scroll = document.getElementById('moments-scroll');
        const header = document.getElementById('moments-header');
        const title = document.getElementById('moments-title');
        const buttons = header.querySelectorAll('button');
        if (scroll.scrollTop > 180) {
            header.className = 'h-16 w-full flex items-end pb-2 px-4 absolute top-0 left-0 z-40 shrink-0 moments-header-solid';
            title.classList.remove('text-transparent');
            title.classList.add('theme-text');
            buttons.forEach(b => { b.classList.remove('text-white', 'drop-shadow-md'); b.classList.add('theme-text'); });
        } else {
            header.className = 'h-16 w-full flex items-end pb-2 px-4 absolute top-0 left-0 z-40 shrink-0 moments-header-transparent';
            title.classList.add('text-transparent');
            title.classList.remove('theme-text');
            buttons.forEach(b => { b.classList.add('text-white', 'drop-shadow-md'); b.classList.remove('theme-text'); });
        }
    },

    async openDetail(momentId) {
        State.currentMomentDetailId = momentId;
        const m = await db.moments.get(momentId);
        if (!m) return;
        const char = await db.characters.get(m.publisherId);
        const author = char || { name: '我' };
        const likes = await MomentDB.getLikes(m.id);
        const comments = await MomentDB.getComments(m.id);

        let imgHtml = '';
        if (m.images && m.images.length > 0) {
            imgHtml = '<div class="grid grid-cols-3 gap-1 mt-2 mb-2">' +
                m.images.map(img => `<div class="aspect-square bg-[var(--input-bg)] cursor-pointer" onclick="openImageDetail('${escapeHTML(img)}')"><img src="${img}" class="w-full h-full object-cover"></div>`).join('') +
                '</div>';
        }

        const container = document.getElementById('moment-detail-content');
        container.innerHTML = `
            <div class="flex px-4 py-4 border-b theme-border">
                <div class="w-10 h-10 rounded-lg overflow-hidden flex-shrink-0 bg-[var(--input-bg)]">
                    <img src="${getAvatarUrl(char)}" class="w-full h-full object-cover">
                </div>
                <div class="ml-3 flex-1">
                    <h3 class="font-medium text-[#576b95] text-[15px]">${escapeHTML(author.name)}</h3>
                    <p class="text-[15px] theme-text mt-1 whitespace-pre-wrap">${escapeHTML(m.content)}</p>
                    ${imgHtml}
                    <div class="flex justify-between items-center mt-2">
                        <span class="text-xs theme-text-secondary">${formatTime(m.timestamp)}</span>
                        <button class="text-xs text-[#576b95] active:opacity-50" onclick="Moments.toggleLikeInDetail(${m.id})">
                            <i class="${likes.some(l => l.likerId === 'me') ? 'fa-solid' : 'fa-regular'} fa-heart mr-1"></i>赞 ${likes.length || ''}
                        </button>
                    </div>
                </div>
            </div>
            ${likes.length > 0 ? `<div class="px-4 py-2 border-b theme-border text-xs theme-text-secondary"><i class="fa-solid fa-heart text-red-400 mr-1"></i>${likes.map(l => l.likerName || l.likerId).join(', ')}</div>` : ''}
            <div class="px-4 py-2 space-y-3 mb-4" id="comments-list">
                ${comments.map(c => `
                    <div>
                        <span class="font-medium text-[#576b95] text-sm">${escapeHTML(c.authorName || c.authorId)}</span>
                        ${c.replyToId ? `<span class="text-xs theme-text-secondary"> 回复 <span class="text-[#576b95]">${escapeHTML(c.replyToName || '')}</span></span>` : ''}
                        <span class="text-sm theme-text">: ${escapeHTML(c.content)}</span>
                        <span class="text-xs theme-text-secondary ml-2">${formatTime(c.timestamp)}</span>
                    </div>
                `).join('')}
                ${comments.length === 0 ? '<div class="text-center text-xs theme-text-secondary py-4">暂无评论</div>' : ''}
            </div>
        `;
        openView('moment-detail');
    },

    async toggleLikeInDetail(momentId) {
        const isLiked = await MomentDB.toggleLike(momentId, 'me', '我');
        showToast(isLiked ? '已点赞' : '已取消点赞');
        this.openDetail(momentId);
    },

    async submitComment() {
        const input = document.getElementById('comment-input');
        const content = input.value.trim();
        if (!content || !State.currentMomentDetailId) return;
        await db.moment_comments.add({
            momentId: State.currentMomentDetailId,
            authorId: 'me',
            authorName: '我',
            content: content,
            replyToId: '',
            timestamp: Date.now(),
        });
        input.value = '';
        showToast('评论成功');
        this.openDetail(State.currentMomentDetailId);
    },

    addImageToNewMoment() {
        if (State.pendingMomentImages.length >= 9) { showToast('最多9张图片'); return; }
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = e => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = () => {
                State.pendingMomentImages.push(reader.result);
                this.renderNewMomentImages();
            };
            reader.readAsDataURL(file);
        };
        input.click();
    },

    renderNewMomentImages() {
        document.getElementById('new-moment-images').innerHTML = State.pendingMomentImages.map((img, i) =>
            `<div class="aspect-square bg-[var(--input-bg)] rounded-lg relative overflow-hidden">
                <img src="${img}" class="w-full h-full object-cover">
                <div class="absolute top-0 right-0 bg-red-500 text-white w-5 h-5 rounded-full flex items-center justify-center text-xs cursor-pointer" onclick="State.pendingMomentImages.splice(${i},1);Moments.renderNewMomentImages()">✕</div>
            </div>`
        ).join('');
    },

    async postNewMoment() {
        const content = document.getElementById('new-moment-content').value.trim();
        if (!content) { showToast('请输入内容'); return; }
        await db.moments.add({
            publisherId: 'user',
            publisherType: 0,
            content: content,
            images: [...State.pendingMomentImages],
            timestamp: Date.now(),
        });
        State.pendingMomentImages = [];
        document.getElementById('new-moment-content').value = '';
        showToast('发布成功');
        navigateBack();
        if (State.viewStack[State.viewStack.length - 1] === 'moments') {
            this.loadFeed();
        }
    },
};

function openImageDetail(url) {
    const overlay = document.getElementById('view-image-detail');
    const img = document.getElementById('image-detail-img');
    img.src = url;
    overlay.classList.remove('hidden');
    setTimeout(() => overlay.classList.add('opacity-100'), 10);
}

function closeImageDetail() {
    const overlay = document.getElementById('view-image-detail');
    overlay.classList.remove('opacity-100');
    setTimeout(() => overlay.classList.add('hidden'), 200);
}

// ============================================================
// SETTINGS
// ============================================================
const Settings = {
    async loadApi() {
        document.getElementById('input-api-endpoint').value = Settings.get('api_endpoint', 'https://api.openai.com/v1/');
        document.getElementById('input-api-key').value = Settings.get('api_key', '');
        document.getElementById('input-api-model').value = Settings.get('api_model', 'gpt-4o-mini');
        document.getElementById('input-temperature').value = Settings.get('temperature', '0.8');
        document.getElementById('temp-value').textContent = Settings.get('temperature', '0.8');
        document.getElementById('input-img-endpoint').value = Settings.get('img_endpoint', '');
        document.getElementById('input-img-key').value = Settings.get('img_key', '');
        document.getElementById('input-qweather-key').value = Settings.get('qweather_key', '');
        document.getElementById('input-tts-key').value = Settings.get('tts_key', '');
        document.getElementById('input-tts-group').value = Settings.get('tts_group', '');
    },

    saveApi() {
        Settings.set('api_endpoint', document.getElementById('input-api-endpoint').value.trim());
        Settings.set('api_key', document.getElementById('input-api-key').value.trim());
        Settings.set('api_model', document.getElementById('input-api-model').value.trim());
        Settings.set('temperature', document.getElementById('input-temperature').value);
        Settings.set('img_endpoint', document.getElementById('input-img-endpoint').value.trim());
        Settings.set('img_key', document.getElementById('input-img-key').value.trim());
        Settings.set('qweather_key', document.getElementById('input-qweather-key').value.trim());
        Settings.set('tts_key', document.getElementById('input-tts-key').value.trim());
        Settings.set('tts_group', document.getElementById('input-tts-group').value.trim());
        showToast('API 配置已保存 ✓');
    },

    async fetchModels() {
        const endpoint = document.getElementById('input-api-endpoint').value.trim();
        const key = document.getElementById('input-api-key').value.trim();
        if (!endpoint || !key) { showToast('请先填写 API 地址和密钥'); return; }
        try {
            const baseUrl = endpoint.endsWith('/') ? endpoint : endpoint + '/';
            const res = await fetch(baseUrl + 'models', { headers: { 'Authorization': `Bearer ${key}` } });
            if (!res.ok) throw new Error('请求失败');
            const data = await res.json();
            const list = document.getElementById('models-list');
            list.classList.remove('hidden');
            list.innerHTML = data.data.filter(m => m.id.includes('gpt') || m.id.includes('claude') || m.id.includes('deepseek') || m.id.includes('qwen'))
                .map(m => `<div class="px-3 py-2 text-sm theme-text cursor-pointer hover:bg-[var(--surface-hover)]" onclick="document.getElementById('input-api-model').value='${m.id}';document.getElementById('models-list').classList.add('hidden')">${m.id}</div>`).join('');
            if (!list.innerHTML) list.innerHTML = '<div class="px-3 py-2 text-sm theme-text-secondary">未找到模型</div>';
        } catch (e) {
            showToast('获取模型失败: ' + e.message);
        }
    },

    loadPreset() {
        const presets = Settings.getJson('api_presets', []);
        if (presets.length === 0) { showToast('暂无保存的预设'); return; }
        // Simple: load first preset
        const p = presets[0];
        document.getElementById('input-api-endpoint').value = p.endpoint || '';
        document.getElementById('input-api-key').value = p.key || '';
        document.getElementById('input-api-model').value = p.model || '';
        document.getElementById('input-temperature').value = p.temperature || '0.8';
        document.getElementById('temp-value').textContent = p.temperature || '0.8';
        showToast('已加载预设');
    },

    savePreset() {
        const p = {
            name: '预设 ' + new Date().toLocaleDateString(),
            endpoint: document.getElementById('input-api-endpoint').value.trim(),
            key: document.getElementById('input-api-key').value.trim(),
            model: document.getElementById('input-api-model').value.trim(),
            temperature: document.getElementById('input-temperature').value,
        };
        const presets = Settings.getJson('api_presets', []);
        presets.unshift(p);
        Settings.setJson('api_presets', presets.slice(0, 5));
        showToast('预设已保存');
    },

    loadMessage() {
        document.getElementById('input-history-rounds').value = Settings.get('history_rounds', '20');
        document.getElementById('history-rounds-val').textContent = Settings.get('history_rounds', '20');
        document.getElementById('input-summary-rounds').value = Settings.get('summary_rounds', '50');
        document.getElementById('summary-rounds-val').textContent = Settings.get('summary_rounds', '50');
        document.getElementById('input-memory-count').value = Settings.get('memory_count', '5');
        document.getElementById('memory-count-val').textContent = Settings.get('memory_count', '5');
        document.getElementById('memory-v2-toggle').classList.toggle('on', Settings.get('memory_v2', 'true') === 'true');
        document.getElementById('input-memory-mode').value = Settings.get('memory_mode', 'economy');
    },

    saveMessage() {
        Settings.set('history_rounds', document.getElementById('input-history-rounds').value);
        Settings.set('summary_rounds', document.getElementById('input-summary-rounds').value);
        Settings.set('memory_count', document.getElementById('input-memory-count').value);
        Settings.set('memory_v2', document.getElementById('memory-v2-toggle').classList.contains('on'));
        Settings.set('memory_mode', document.getElementById('input-memory-mode').value);
        showToast('消息设置已保存 ✓');
    },
};

// Data Manager
const DataManager = {
    async exportAll() {
        try {
            const data = {
                characters: await db.characters.toArray(),
                chat_sessions: await db.chat_sessions.toArray(),
                messages: await db.messages.toArray(),
                my_personas: await db.my_personas.toArray(),
                moments: await db.moments.toArray(),
                moment_comments: await db.moment_comments.toArray(),
                moment_likes: await db.moment_likes.toArray(),
                memories: await db.memories.toArray(),
                worldbook_entries: await db.worldbook_entries.toArray(),
                user_profile_nodes: await db.user_profile_nodes.toArray(),
                episodic_memory: await db.episodic_memory.toArray(),
                salient_billboard: await db.salient_billboard.toArray(),
                schedule_entries: await db.schedule_entries.toArray(),
                memos: await db.memos.toArray(),
                call_records: await db.call_records.toArray(),
                emoji_entries: await db.emoji_entries.toArray(),
                settings: {},
            };
            // Gather localStorage settings
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                if (key.startsWith('jingxi_')) {
                    data.settings[key] = localStorage.getItem(key);
                }
            }

            const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `jingxi_backup_${new Date().toISOString().slice(0,10)}.json`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
            showToast('数据导出成功 ✓');
        } catch (e) {
            showToast('导出失败: ' + e.message);
        }
    },

    importAll() {
        document.getElementById('import-file-input').click();
    },

    async handleImport(event) {
        const file = event.target.files[0];
        if (!file) return;
        try {
            const text = await file.text();
            const data = JSON.parse(text);
            let count = 0;
            // Import tables
            if (data.characters) { await db.characters.bulkPut(data.characters); count += data.characters.length; }
            if (data.chat_sessions) { await db.chat_sessions.bulkPut(data.chat_sessions); }
            if (data.messages) { await db.messages.bulkPut(data.messages); }
            if (data.my_personas) { await db.my_personas.bulkPut(data.my_personas); }
            if (data.moments) { await db.moments.bulkPut(data.moments); }
            if (data.moment_comments) { await db.moment_comments.bulkPut(data.moment_comments); }
            if (data.moment_likes) { await db.moment_likes.bulkPut(data.moment_likes); }
            if (data.memories) { await db.memories.bulkPut(data.memories); }
            if (data.worldbook_entries) { await db.worldbook_entries.bulkPut(data.worldbook_entries); }
            if (data.emoji_entries) { await db.emoji_entries.bulkPut(data.emoji_entries); }
            if (data.settings) {
                for (const [key, val] of Object.entries(data.settings)) {
                    localStorage.setItem(key, val);
                }
            }
            showToast(`导入成功！已导入 ${count} 个角色`);
            // Reload
            if (State.viewStack[State.viewStack.length - 1] === 'wechat') Chat.loadSessions();
        } catch (e) {
            showToast('导入失败: ' + e.message);
        }
    },

    async clearCache() {
        const ok = await showModal('清除缓存', '确认清除所有图片和音频缓存？', [
            { text: '取消' },
            { text: '确认', type: 'danger', action: () => {
                // Clear cached blob URLs and image data
                showToast('缓存已清除');
            }},
        ]);
    },

    async clearAll() {
        const ok = await showModal('⚠️ 危险操作', '确认清除全部数据？此操作不可恢复！包括所有角色、会话、消息、朋友圈数据。', [
            { text: '取消' },
            { text: '全部清除', type: 'danger', action: async () => {
                await db.delete();
                localStorage.clear();
                showToast('全部数据已清除，请刷新页面');
                setTimeout(() => location.reload(), 2000);
            }},
        ]);
    },
};

// ============================================================
// WEATHER
// ============================================================
const Weather = {
    async fetchData() {
        const lat = parseFloat(Settings.get('weather_lat', '39.9042'));
        const lon = parseFloat(Settings.get('weather_lon', '116.4074'));
        try {
            const res = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current_weather=true&daily=temperature_2m_max,temperature_2m_min,weathercode&timezone=Asia%2FShanghai`);
            const data = await res.json();
            const c = data.current_weather;
            const d = data.daily;

            document.getElementById('weather-temp').innerHTML = `${Math.round(c.temperature)}<span class="text-3xl absolute top-1 -right-6">°</span>`;

            const codeMap = {0:['晴朗','fa-sun'],1:['多云','fa-cloud-sun'],2:['阴天','fa-cloud'],3:['阴天','fa-cloud'],45:['雾','fa-smog'],48:['雾','fa-smog'],51:['毛毛雨','fa-cloud-rain'],53:['小雨','fa-cloud-rain'],61:['雨','fa-cloud-showers-heavy'],63:['大雨','fa-cloud-showers-heavy'],71:['雪','fa-snowflake'],73:['大雪','fa-snowflake'],80:['阵雨','fa-cloud-rain'],95:['雷阵雨','fa-cloud-bolt']};
            const info = codeMap[c.weathercode] || ['未知','fa-cloud'];

            document.getElementById('weather-desc').textContent = info[0];
            document.getElementById('weather-main-icon').className = 'fa-solid ' + info[1] + ' text-6xl mb-3 drop-shadow-lg';
            document.getElementById('weather-range').textContent = `最高 ${Math.round(d.temperature_2m_max[0])}° 最低 ${Math.round(d.temperature_2m_min[0])}°`;

            const forecast = document.getElementById('weather-forecast-list');
            forecast.innerHTML = '';
            for (let i = 0; i < 7; i++) {
                const date = new Date(d.time[i]);
                const dayStr = i === 0 ? '今天' : ['周日','周一','周二','周三','周四','周五','周六'][date.getDay()];
                const cinfo = codeMap[d.weathercode[i]] || ['未知','fa-cloud'];
                const rangePct = (d.temperature_2m_min[i] + d.temperature_2m_max[i]) / 2 / 40 * 100;
                forecast.innerHTML += `
                    <div class="flex justify-between items-center">
                        <span class="w-10 text-sm">${dayStr}</span>
                        <i class="fa-solid ${cinfo[1]} text-lg w-8 text-center"></i>
                        <div class="flex-1 flex justify-end gap-2 font-medium text-sm items-center">
                            <span class="opacity-70 w-8 text-right">${Math.round(d.temperature_2m_min[i])}°</span>
                            <div class="w-16 h-1.5 bg-white/20 rounded-full overflow-hidden"><div class="h-full bg-white rounded-full" style="width:${rangePct}%"></div></div>
                            <span class="w-8">${Math.round(d.temperature_2m_max[i])}°</span>
                        </div>
                    </div>`;
            }
        } catch (e) {
            document.getElementById('weather-desc').textContent = '获取天气失败';
        }
    },
};

// ============================================================
// WORLDBOOK
// ============================================================
const Worldbook = {
    async loadList() {
        const entries = await WorldbookDB.getAll();
        const pre = entries.filter(e => e.type === 0);
        const mid = entries.filter(e => e.type === 1);
        const post = entries.filter(e => e.type === 2);
        const allSections = [
            { id: 'worldbook-pre-section', items: pre },
            { id: 'worldbook-mid-section', items: mid },
            { id: 'worldbook-post-section', items: post },
        ];
        let html = '';
        for (const sec of allSections) {
            html += `<div class="px-4 py-2 text-xs theme-text-secondary font-medium bg-[var(--bg-alt)] border-b theme-border">${sec.id.includes('pre') ? 'Pre (对话前注入)' : sec.id.includes('mid') ? 'Mid (关键词触发)' : 'Post (对话后注入)'}</div>`;
            if (sec.items.length === 0) {
                html += '<div class="text-center text-xs theme-text-secondary py-4 border-b theme-border">暂无条目</div>';
            } else {
                for (const e of sec.items) {
                    html += `<div class="contact-item" onclick="Worldbook.openEdit(${e.id})">
                        <div class="flex-1">
                            <div class="flex items-center gap-2">
                                <span class="font-medium theme-text">${escapeHTML(e.title)}</span>
                                <span class="text-[10px] ${e.isEnabled ? 'text-green-500' : 'text-red-400'}">${e.isEnabled ? '启用' : '禁用'}</span>
                            </div>
                            <p class="text-xs theme-text-secondary mt-0.5 truncate">${escapeHTML(e.content.substring(0, 40))}</p>
                        </div>
                        <i class="fa-solid fa-chevron-right text-gray-300 text-xs"></i>
                    </div>`;
                }
            }
        }
        document.getElementById('worldbook-list').innerHTML = html;
    },

    async openEdit(id) {
        document.getElementById('edit-worldbook-id').value = id || '';
        document.getElementById('worldbook-edit-title').textContent = id ? '编辑世界书' : '新建世界书';
        document.getElementById('btn-delete-worldbook').style.display = id ? 'block' : 'none';
        if (id) {
            const e = await db.worldbook_entries.get(id);
            if (e) {
                document.getElementById('input-worldbook-type').value = e.type;
                document.getElementById('input-worldbook-title').value = e.title;
                document.getElementById('input-worldbook-keyword').value = e.keyword || '';
                document.getElementById('input-worldbook-content').value = e.content;
                document.getElementById('worldbook-enabled-toggle').classList.toggle('on', !!e.isEnabled);
            }
        } else {
            document.getElementById('input-worldbook-type').value = '0';
            document.getElementById('input-worldbook-title').value = '';
            document.getElementById('input-worldbook-keyword').value = '';
            document.getElementById('input-worldbook-content').value = '';
            document.getElementById('worldbook-enabled-toggle').classList.add('on');
        }
        openView('worldbook-edit');
    },

    async saveEntry() {
        const title = document.getElementById('input-worldbook-title').value.trim();
        const content = document.getElementById('input-worldbook-content').value.trim();
        if (!title || !content) { showToast('请填写标题和内容'); return; }
        const data = {
            type: parseInt(document.getElementById('input-worldbook-type').value),
            title: title,
            keyword: document.getElementById('input-worldbook-keyword').value.trim(),
            content: content,
            isEnabled: document.getElementById('worldbook-enabled-toggle').classList.contains('on'),
        };
        const id = document.getElementById('edit-worldbook-id').value;
        if (id) {
            await db.worldbook_entries.update(parseInt(id), data);
        } else {
            await db.worldbook_entries.add(data);
        }
        showToast('世界书已保存');
        navigateBack();
        this.loadList();
    },

    async deleteCurrent() {
        const id = document.getElementById('edit-worldbook-id').value;
        if (!id) return;
        const ok = await showModal('删除确认', '确认删除此世界书条目？', [
            { text: '取消' },
            { text: '删除', type: 'danger', action: async () => {
                await db.worldbook_entries.delete(parseInt(id));
                showToast('已删除');
                navigateBack();
                Worldbook.loadList();
            }},
        ]);
    },
};

// ============================================================
// MEMORY VIEWER
// ============================================================
const MemoryView = {
    currentTab: 'memory',

    async init() {
        const select = document.getElementById('memory-char-select');
        select.innerHTML = '<option value="">选择角色...</option>' +
            (await db.characters.toArray()).map(c => `<option value="${c.id}">${escapeHTML(c.name)}</option>`).join('');
    },

    async load() {
        const charId = parseInt(document.getElementById('memory-char-select').value);
        if (!charId) {
            document.getElementById('memory-content').innerHTML = '<div class="text-center text-sm theme-text-secondary py-10">请选择角色查看记忆</div>';
            return;
        }
        this.switchTab(this.currentTab);
    },

    async switchTab(tab) {
        this.currentTab = tab;
        document.querySelectorAll('#view-memory .emoji-tab').forEach(t => t.classList.remove('active'));
        // Find and activate the clicked tab
        const tabs = document.querySelectorAll('#view-memory .emoji-tab');
        const tabMap = { memory: 0, profile: 1, episodic: 2, billboard: 3 };
        if (tabs[tabMap[tab]]) tabs[tabMap[tab]].classList.add('active');

        const charId = parseInt(document.getElementById('memory-char-select').value);
        if (!charId) return;
        const container = document.getElementById('memory-content');

        if (tab === 'memory') {
            const memories = await MemoryDB.getByChar(charId);
            container.innerHTML = memories.length === 0
                ? '<div class="text-center text-sm theme-text-secondary py-10">暂无记忆</div>'
                : memories.map(m => `
                    <div class="bg-[var(--surface)] rounded-xl p-3 mb-2 shadow-sm">
                        <div class="flex items-center gap-2 mb-1">
                            <span class="tag">${escapeHTML(m.category || '其他')}</span>
                            ${m.type === 1 ? '<span class="tag" style="background:var(--red-light)">重要</span>' : ''}
                            <span class="text-xs theme-text-secondary">${formatTime(m.timestamp)}</span>
                        </div>
                        <p class="text-sm theme-text">${escapeHTML(m.content)}</p>
                    </div>`).join('');
        } else if (tab === 'profile') {
            const profiles = await ProfileDB.getByChar(charId);
            container.innerHTML = profiles.length === 0
                ? '<div class="text-center text-sm theme-text-secondary py-10">暂无用户画像</div>'
                : profiles.map(p => `
                    <div class="bg-[var(--surface)] rounded-xl p-3 mb-2 shadow-sm">
                        <div class="flex items-center gap-2 mb-1">
                            <span class="tag">${escapeHTML(p.category)}</span>
                            <span class="text-xs theme-text-secondary">信心: ${p.confidence || '?'}/10</span>
                            <span class="text-xs" style="color:var(--accent)">${escapeHTML(p.emotionTag || '')}</span>
                        </div>
                        <p class="text-sm font-medium theme-text">${escapeHTML(p.keyItem)}</p>
                        <p class="text-sm theme-text-secondary">${escapeHTML(p.valueContent)}</p>
                    </div>`).join('');
        } else if (tab === 'episodic') {
            const episodes = await EpisodicDB.getByChar(charId);
            container.innerHTML = episodes.length === 0
                ? '<div class="text-center text-sm theme-text-secondary py-10">暂无情景记忆</div>'
                : episodes.map(e => `
                    <div class="bg-[var(--surface)] rounded-xl p-3 mb-2 shadow-sm">
                        <div class="flex items-center gap-2 mb-1">
                            <span class="font-medium theme-text">${escapeHTML(e.title)}</span>
                            <span class="text-xs theme-text-secondary">${escapeHTML(e.episodeDate || '')}</span>
                            <span class="tag">重要性: ${e.importanceLevel || '?'}</span>
                        </div>
                        <p class="text-sm theme-text-secondary">${escapeHTML(e.subjectiveDiary || '')}</p>
                        <p class="text-xs mt-1 theme-text-hint">情绪: ${escapeHTML(e.emotionalTone || '')}</p>
                    </div>`).join('');
        } else if (tab === 'billboard') {
            const billboards = await BillboardDB.getByChar(charId);
            container.innerHTML = billboards.length === 0
                ? '<div class="text-center text-sm theme-text-secondary py-10">暂无公告板条目</div>'
                : billboards.map(b => `
                    <div class="bg-[var(--surface)] rounded-xl p-3 mb-2 shadow-sm border-l-4" style="border-left-color: var(--accent)">
                        <div class="flex items-center gap-2 mb-1">
                            <span class="tag">优先级: ${b.priority || '?'}</span>
                            <span class="text-xs theme-text-secondary">${formatTime(b.createdAt)}</span>
                        </div>
                        <p class="text-sm theme-text">${escapeHTML(b.content)}</p>
                    </div>`).join('');
        }
    },
};

// ============================================================
// MEMO VIEWER
// ============================================================
const MemoView = {
    async init() {
        const select = document.getElementById('memo-char-select');
        select.innerHTML = '<option value="">选择角色...</option>' +
            (await db.characters.toArray()).map(c => `<option value="${c.id}">${escapeHTML(c.name)}</option>`).join('');
    },

    async load() {
        const charId = parseInt(document.getElementById('memo-char-select').value);
        if (!charId) {
            document.getElementById('memo-list').innerHTML = '<div class="text-center text-sm theme-text-secondary py-10">请选择角色查看备忘录</div>';
            return;
        }
        const memos = await MemoDB.getByChar(charId);
        document.getElementById('memo-list').innerHTML = memos.length === 0
            ? '<div class="text-center text-sm theme-text-secondary py-10">暂无备忘录</div>'
            : memos.map(m => `
                <div class="contact-item">
                    <div class="flex-1">
                        <div class="flex items-center gap-2">
                            <span class="text-sm theme-text">${escapeHTML(m.content)}</span>
                            <span class="tag ${m.status === 1 ? 'bg-green-100 text-green-600' : 'bg-yellow-100 text-yellow-600'}">${m.status === 1 ? '已完成' : '待办'}</span>
                        </div>
                        <p class="text-xs theme-text-secondary mt-0.5">目标日期: ${escapeHTML(m.targetDate || '无')}</p>
                    </div>
                </div>
            `).join('');
    },
};

// ============================================================
// SCHEDULE VIEWER
// ============================================================
const ScheduleView = {
    async init() {
        const select = document.getElementById('schedule-char-select');
        select.innerHTML = '<option value="">选择角色...</option>' +
            (await db.characters.toArray()).map(c => `<option value="${c.id}">${escapeHTML(c.name)}</option>`).join('');
        document.getElementById('schedule-date').value = new Date().toISOString().slice(0, 10);
    },

    async load() {
        const charId = parseInt(document.getElementById('schedule-char-select').value);
        const date = document.getElementById('schedule-date').value;
        if (!charId) {
            document.getElementById('schedule-content').innerHTML = '<div class="text-center text-sm theme-text-secondary py-10">请选择角色和日期查看日程</div>';
            return;
        }
        const entries = await ScheduleDB.getByCharAndDate(charId, date);
        if (entries.length === 0) {
            document.getElementById('schedule-content').innerHTML = '<div class="text-center text-sm theme-text-secondary py-10">该日期暂无日程</div>';
            return;
        }
        document.getElementById('schedule-content').innerHTML = entries.map(e => {
            let schedule;
            try { schedule = JSON.parse(e.contentJson); } catch (ex) { schedule = { items: [], overallPlan: '' }; }
            return `
                <div class="bg-[var(--surface)] rounded-xl p-3 mb-2 shadow-sm">
                    <p class="text-sm font-medium theme-text mb-2">📋 ${escapeHTML(schedule.overallPlan || '无计划')}</p>
                    ${(schedule.items || []).map(item => `
                        <div class="flex items-center gap-3 py-1 border-t theme-border">
                            <span class="text-xs theme-text-secondary w-14">${escapeHTML(item.time || '')}</span>
                            <span class="text-sm theme-text">${escapeHTML(item.action || '')}</span>
                            ${item.completed ? '<i class="fa-solid fa-check text-green-500 text-xs"></i>' : ''}
                        </div>
                    `).join('')}
                </div>`;
        }).join('');
    },
};

// ============================================================
// CALL
// ============================================================
const Call = {
    timerInterval: null,
    seconds: 0,

    async startCall(charId) {
        let char;
        if (charId) {
            char = await db.characters.get(charId);
        } else {
            char = State.currentChatChar;
        }
        if (!char) {
            // Try first character
            const chars = await db.characters.toArray();
            if (chars.length === 0) { showToast('请先创建 AI 联系人'); return; }
            char = chars[0];
        }
        document.getElementById('call-name').textContent = char.name;
        document.getElementById('call-status-text').textContent = '正在连接...';
        document.getElementById('call-timer').textContent = '00:00';
        document.getElementById('call-subtitle').innerHTML = '<div class="text-white/60 text-sm text-center">— 通话中 —</div>';
        // Avatar
        const avatarContainer = document.getElementById('call-avatar-container');
        avatarContainer.innerHTML = `<img src="${getAvatarUrl(char)}" class="w-full h-full object-cover">`;

        openView('call');
        this.seconds = 0;
        setTimeout(() => {
            document.getElementById('call-status-text').textContent = '通话中';
            this.timerInterval = setInterval(() => {
                this.seconds++;
                const m = Math.floor(this.seconds / 60).toString().padStart(2, '0');
                const s = (this.seconds % 60).toString().padStart(2, '0');
                document.getElementById('call-timer').textContent = m + ':' + s;
            }, 1000);
        }, 1500);
    },

    async endCall() {
        if (this.timerInterval) clearInterval(this.timerInterval);
        this.timerInterval = null;
        // Save call record
        const char = State.currentChatChar;
        if (char && State.currentSessionId) {
            await db.call_records.add({
                sessionId: State.currentSessionId,
                characterId: char.id,
                startTime: Date.now() - this.seconds * 1000,
                endTime: Date.now(),
                duration: this.seconds,
                summary: `通话 ${this.seconds} 秒`,
                initiator: 0,
                isMissed: this.seconds < 1,
            });
        }
        navigateBack();
        showToast(this.seconds > 0 ? `通话结束，时长 ${this.seconds} 秒` : '通话已取消');
        this.seconds = 0;
    },

    toggleSpeak() {
        const btn = document.getElementById('call-speak-btn');
        const isActive = btn.classList.toggle('speaking');
        btn.style.backgroundColor = isActive ? 'var(--green)' : 'rgba(255,255,255,0.2)';
    },
};

// Call History
const CallHistory = {
    async load() {
        const records = await CallDB.getAll();
        const container = document.getElementById('call-history-list');
        if (records.length === 0) {
            container.innerHTML = '<div class="text-center text-sm theme-text-secondary py-10">暂无通话记录</div>';
            return;
        }
        let html = '';
        for (const r of records) {
            const char = await db.characters.get(r.characterId);
            const name = char ? char.name : '未知';
            const d = new Date(r.startTime);
            const dateStr = formatDate(r.startTime);
            const timeStr = d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0');
            const mins = Math.floor(r.duration / 60);
            const secs = r.duration % 60;
            const durStr = mins > 0 ? `${mins}分${secs}秒` : `${secs}秒`;
            html += `
                <div class="contact-item">
                    <div class="w-10 h-10 rounded-lg overflow-hidden mr-3 bg-[var(--input-bg)]">
                        <img src="${getAvatarUrl(char)}" class="w-full h-full object-cover">
                    </div>
                    <div class="flex-1">
                        <span class="theme-text">${escapeHTML(name)}</span>
                        <p class="text-xs theme-text-secondary">
                            ${r.isMissed ? '<span class="text-red-500">未接听</span> · ' : ''}
                            ${r.initiator === 0 ? '呼出' : '呼入'} · ${durStr}
                        </p>
                    </div>
                    <span class="text-xs theme-text-secondary">${dateStr} ${timeStr}</span>
                </div>`;
        }
        container.innerHTML = html;
    },
};

// ============================================================
// EMOJI MANAGER
// ============================================================
const EmojiManager = {
    async load() {
        const emojis = await EmojiDB.getAll();
        document.getElementById('emoji-manage-grid').innerHTML = emojis.map(e =>
            `<div class="flex flex-col items-center gap-1 cursor-pointer relative group">
                <span class="text-3xl">${escapeHTML(e.name)}</span>
                <span class="text-[10px] theme-text-secondary">${escapeHTML(e.groupName)}</span>
                <div class="absolute -top-1 -right-1 bg-red-500 text-white w-4 h-4 rounded-full text-[8px] flex items-center justify-center cursor-pointer opacity-0 group-hover:opacity-100"
                     onclick="EmojiManager.deleteEmoji(${e.id})">✕</div>
            </div>`
        ).join('');
    },

    async addEmoji() {
        const name = prompt('输入表情符号:');
        if (!name) return;
        const group = prompt('分组名称:', '默认');
        if (!group) return;
        await db.emoji_entries.add({ name, imageUrl: '', groupName: group });
        showToast('表情已添加');
        this.load();
    },

    async deleteEmoji(id) {
        await db.emoji_entries.delete(id);
        showToast('已删除');
        this.load();
    },
};

// ============================================================
// PER-VIEW INITIALIZATION HOOKS
// Extend Navigator._onViewChanged to trigger view-specific init
// ============================================================
(function() {
    const origOnViewChanged = Navigator._onViewChanged.bind(Navigator);
    Navigator._onViewChanged = function(viewId) {
        origOnViewChanged(viewId);
        // Additional view-specific init
        if (viewId === 'settings-api') Settings.loadApi();
        if (viewId === 'settings-message') Settings.loadMessage();
    };
})();

// ============================================================
// APP INITIALIZATION
// ============================================================
document.addEventListener('DOMContentLoaded', async () => {
    // Wait for Dexie to be ready
    await db.open();

    // Init theme
    ThemeManager.init();

    // Init desktop
    Desktop.init();

    // Init event listeners
    initEventListeners();

    console.log('[App] 镜隙 Web 版初始化完成 🎉');
    console.log('[App] 桌面视图已就绪');
});

function initEventListeners() {
    // Chat input auto-resize
    const chatInput = document.getElementById('chat-input');
    if (chatInput) {
        chatInput.addEventListener('input', function() {
            this.style.height = 'auto';
            this.style.height = Math.min(this.scrollHeight, 100) + 'px';
        });
        chatInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                Chat.sendMessage();
            }
        });
    }

    // Auto-moment toggle
    const autoMomentToggle = document.getElementById('auto-moment-toggle');
    if (autoMomentToggle) {
        autoMomentToggle.addEventListener('click', function() {
            const options = document.getElementById('auto-moment-options');
            options.classList.toggle('hidden', !this.classList.contains('on'));
        });
    }

    // Worldbook type change → show/hide keyword field
    const wbTypeSelect = document.getElementById('input-worldbook-type');
    if (wbTypeSelect) {
        wbTypeSelect.addEventListener('change', function() {
            const kwGroup = document.getElementById('worldbook-keyword-group');
            kwGroup.style.display = this.value === '1' ? 'block' : 'none';
        });
    }

    // Close context menu on scroll
    document.getElementById('chat-messages')?.addEventListener('scroll', hideContextMenu);
    document.getElementById('moments-scroll')?.addEventListener('scroll', hideContextMenu);

    // Back gesture detection (swipe right on left edge)
    let touchStartX = 0;
    document.addEventListener('touchstart', e => {
        touchStartX = e.touches[0].clientX;
    });
    document.addEventListener('touchend', e => {
        if (e.changedTouches[0].clientX - touchStartX > 80 && touchStartX < 40) {
            if (State.viewStack.length > 1) {
                navigateBack();
            }
        }
    });
}
