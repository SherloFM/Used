(function () {
    const TOKEN_KEY = 'used_jwt_token';

    function getToken() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function showLoginRequired(message) {
        // Home page has the existing "Members Only" lock modal — use it when present
        const modal = document.getElementById('lockModal');
        if (modal) {
            modal.style.display = 'flex';
            return;
        }
        // Listing details page falls back to the toast
        if (window.AppErrors) {
            AppErrors.showToast(message, true);
        } else {
            alert(message);
        }
    }

    async function chatRequest(url, options = {}) {
        const token = getToken();
        if (!token) {
            showLoginRequired('Please log in to chat with other members.');
            throw new Error('Please log in to chat.');
        }

        const response = await fetch(url, {
            ...options,
            headers: {
                ...(options.headers || {}),
                'Authorization': 'Bearer ' + token,
                ...(options.body ? { 'Content-Type': 'application/json' } : {})
            }
        });

        if (!response.ok) {
            // Expired/invalid JWT: show a clear message instead of raw Spring error JSON
            if (response.status === 401 || response.status === 403) {
                showLoginRequired('Your session has expired. Please log in again.');
                throw new Error('Your session has expired. Please log in again.');
            }
            const message = await response.text();
            throw new Error(message || `Chat request failed (${response.status}).`);
        }
        return response.status === 204 ? null : response.json();
    }

    function showNotice(container, message) {
        container.replaceChildren();
        const notice = document.createElement('div');
        notice.className = 'chat-notice';
        notice.textContent = message;
        container.appendChild(notice);
    }

    function initHomeChat() {
        const button = document.getElementById('chatAllFloat');
        const panel = document.getElementById('chatAllPanel');
        const list = document.getElementById('chatAllList');
        const closeButton = document.getElementById('closeAllChats');
        if (!button || !panel || !list || !closeButton) return;

        async function loadConversations() {
            showNotice(list, 'Loading chats...');
            try {
                const conversations = await chatRequest('/api/chat/conversations');
                list.replaceChildren();
                if (!conversations.length) {
                    showNotice(list, 'No conversations yet.');
                    return;
                }

                conversations.forEach(conversation => {
                    const item = document.createElement('button');
                    item.type = 'button';
                    item.className = 'chat-all-item';
                    item.style.width = '100%';
                    item.style.textAlign = 'left';
                    item.style.background = 'white';
                    item.style.border = '0';
                    item.textContent = `${conversation.listingTitle} — ${conversation.otherUserName}`;
                    item.addEventListener('click', () => {
                        window.location.href = `/listing/${encodeURIComponent(conversation.listingId)}?conversationId=${encodeURIComponent(conversation.conversationId)}`;
                    });
                    list.appendChild(item);
                });
            } catch (error) {
                showNotice(list, error.message);
            }
        }

        button.addEventListener('click', () => {
            const opening = panel.style.display !== 'flex';
            panel.style.display = opening ? 'flex' : 'none';
            if (opening) loadConversations();
        });
        closeButton.addEventListener('click', () => { panel.style.display = 'none'; });
    }

    function initListingChat() {
        const button = document.getElementById('chatToggle');
        const panel = document.getElementById('chatBox');
        const closeButton = document.getElementById('closeChat');
        const messagesElement = document.getElementById('chatMessages');
        const input = document.getElementById('chatInput');
        const sendButton = document.getElementById('sendChat');
        const title = document.getElementById('chatTitle');
        if (!button || !panel || !closeButton || !messagesElement || !input || !sendButton) return;

        const listingId = window.location.pathname.split('/').filter(Boolean).pop();
        let conversationId = null;
        let loadingMessages = false;
        let messagesLoaded = false;
        const displayedMessageIds = new Set();

        function addMessage(message) {
            const bubble = document.createElement('div');
            bubble.className = `chat-msg ${message.mine ? 'mine' : 'other'}`;
            bubble.textContent = message.content;
            messagesElement.appendChild(bubble);
            displayedMessageIds.add(message.id);
            messagesElement.scrollTop = messagesElement.scrollHeight;
        }

        async function loadMessages() {
            if (!conversationId || loadingMessages || panel.style.display !== 'flex') return;
            loadingMessages = true;
            try {
                const messages = await chatRequest(`/api/chat/conversation/${encodeURIComponent(conversationId)}/messages`);
                if (!messagesLoaded) {
                    messagesElement.replaceChildren();
                    messagesLoaded = true;
                }
                messages.filter(message => !displayedMessageIds.has(message.id)).forEach(addMessage);
            } catch (error) {
                showNotice(messagesElement, error.message);
            } finally {
                loadingMessages = false;
            }
        }

        async function openConversation() {
            const token = getToken();
            if (!token) {
                showLoginRequired('Please log in to chat with the seller.');
                return;
            }
            panel.style.display = 'flex';
            messagesElement.replaceChildren();
            displayedMessageIds.clear();
            messagesLoaded = false;
            showNotice(messagesElement, 'Opening chat...');
            try {
                const response = await chatRequest(`/api/chat/conversation?listingId=${encodeURIComponent(listingId)}`, {
                    method: 'POST'
                });
                conversationId = response.conversationId;
                if (!conversationId) throw new Error('The conversation could not be opened.');
                if (title) title.textContent = 'Chat with seller';
                await loadMessages();
                input.focus();
            } catch (error) {
                showNotice(messagesElement, error.message);
            }
        }

        async function sendMessage() {
            const content = input.value.trim();
            if (!content || !conversationId) return;
            sendButton.disabled = true;
            try {
                const message = await chatRequest(`/api/chat/conversation/${encodeURIComponent(conversationId)}/messages`, {
                    method: 'POST',
                    body: JSON.stringify({ content })
                });
                input.value = '';
                addMessage(message);
            } catch (error) {
                showNotice(messagesElement, error.message);
            } finally {
                sendButton.disabled = false;
            }
        }

        button.addEventListener('click', openConversation);
        closeButton.addEventListener('click', () => { panel.style.display = 'none'; });
        sendButton.addEventListener('click', sendMessage);
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter') sendMessage();
        });
        window.setInterval(loadMessages, 3000);

        const existingConversationId = new URLSearchParams(window.location.search).get('conversationId');
        if (existingConversationId) {
            conversationId = existingConversationId;
            panel.style.display = 'flex';
            if (title) title.textContent = 'Chat';
            loadMessages();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            initHomeChat();
            initListingChat();
        });
    } else {
        initHomeChat();
        initListingChat();
    }
})();
