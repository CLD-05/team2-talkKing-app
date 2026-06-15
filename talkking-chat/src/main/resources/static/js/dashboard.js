// 🎯 [수정] 새로운 백엔드 인증 규칙에 맞게 경로 변경 및 리프레시 토큰 자동 재발급 메커니즘 탑재
// 🍪 [쿠키 파서 엔진] 브라우저 쿠키에서 특정 키의 값을 추출합니다.
function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift().trim();
    return null;
}

// 🎯 브라우저 쿠키에서 액세스 토큰을 0순위로 획득합니다.
let token = getCookie('accessToken');

// 🔒 초기 구동 시 액세스 토큰이 만료/유실되었다면, 쿠키에 살아있는 리프레시 토큰을 검증합니다.
if (!token && window.location.pathname !== '/login') {
    const refreshToken = getCookie('refreshToken'); // 🍪 쿠키에서 리프레시 토큰 파싱
    
    if (refreshToken) {
        console.log("🔄 [쿠키 엔진] 액세스 토큰 만료 감지. 리프레시 토큰으로 자동 연장을 시도합니다...");
        
        // 백엔드 명세에 맞춰 동기식(XHR)으로 빠르게 재발급 API 호출
        const xhr = new XMLHttpRequest();
        xhr.open("POST", "/api/users/reissue", false); // 동기식 블로킹 처리
        xhr.setRequestHeader("Content-Type", "application/json");
        
        // 만약 백엔드가 쿠키를 알아서 읽는 구조라면 그냥 빈 바디를 보내도 되지만, 
        // 컨트롤러 JSON DTO 매핑 안전성을 위해 기존 Body 구조를 유지하여 전송합니다.
        xhr.send(JSON.stringify({ refreshToken: refreshToken }));

        if (xhr.status === 200) {
            // 💡 성공 시 백엔드가 응답 쿠키로 다시 구워줬거나, JSON으로 내려준 토큰 쌍을 매핑합니다.
            try {
                const data = JSON.parse(xhr.responseText);
                
                // 백엔드가 컨트롤러에서 직접 쿠키를 구워주지 않는 흐름을 대비한 프론트 안전장치 적재
                document.cookie = `accessToken=${data.accessToken}; path=/; max-age=1800;`;
                document.cookie = `refreshToken=${data.refreshToken}; path=/; max-age=1209600;`;
                
                token = data.accessToken; // 글로벌 토큰 변수에 할당하여 시스템 기동 허용
                console.log("✅ [쿠키 엔진] 토큰 자동 로테이션(RTR) 성공. 메인 대시보드를 유지합니다.");
            } catch (e) {
                // 백엔드가 응답 헤더(Set-Cookie)로만 처리하고 바디가 비어있을 경우의 예외 방어
                token = getCookie('accessToken');
                console.log("✅ [인프라 헤더 인증] 쿠키가 브라우저에 자정 반영되었습니다.");
            }
        } else {
            alert('인증이 만료되었습니다. 다시 로그인해주세요.');
            window.location.href = '/login';
            throw new Error("인증 자격 증명 수명 만료");
        }
    } else {
        // 두 토큰 다 없으면 완벽한 비인증 유저이므로 로그인 창 튕구기
        window.location.href = '/login';
        throw new Error("비인증 유저 접근 제한");
    }
}

let stompClient = null;
let currentRoomId = null;
let myRealUserId = null; 
let modalMode = "CREATE"; 
let unreadCounts = {}; 
let activeSubscriptions = {}; 

let currentChatPage = 0;
let hasNextChatPage = true;
let isChatLoading = false;
const chatPageSize = 20;
const realTimeLastMessages = {};

// 🔓 JWT 토큰 해독 및 내부 유저 식별자 추출
if (token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const payload = JSON.parse(window.atob(base64));
        myRealUserId = payload.userId || payload.id || payload.sub || payload.username;
        console.log("🔓 내 로그인 ID 확인 완료:", myRealUserId);
    } catch (e) { 
        console.error("토큰 해독 실패:", e); 
    }
}

document.addEventListener("DOMContentLoaded", () => {
    // 로그인 페이지가 아닐 때만 대시보드 엔진 가동
    if (window.location.pathname !== '/login' && token) {
        console.log("🚀 [초기화 엔진] 시스템 기동 완료. 최초 방 목록 동기화를 시작합니다.");
        
        loadMyChatRooms().then(() => {
            setTimeout(() => {
                console.log("🤖 [폴링 엔진 활성화] 4초 주기 백그라운드 초대 감시를 시작합니다.");
                setInterval(async () => {
                    try {
                        if (stompClient === null || !stompClient.connected) return;

                        const response = await fetch('/api/chat/rooms', {
                            method: 'GET',
                            headers: { 'Authorization': `Bearer ${token}` }
                        });
                        const rooms = await response.json();
                        
                        rooms.forEach(room => {
                            if (!activeSubscriptions[room.roomId]) {
                                console.log(`📢 [실시간 폴링 감지] #${room.roomId}번 방 신규 초대 포착!`);
                                subscribeToRoom(room.roomId);
                                loadMyChatRooms(); 
                            }
                        });
                    } catch (e) { console.error("백그라운드 방 동기화 에러:", e); }
                }, 4000);
            }, 3000); 
        });
    }
});

function connectWebSocket(roomList) {
    const rooms = roomList || [];

    function connectChatServer() {
        // 🔥 [AWS EKS 인프라 패치] ALB 스케일 아웃 시 세션이 튀는 현상을 막기 위해 순수 websocket 프로토콜만 사용하도록 제한 규칙을 추가합니다.
        const chatSocket = new SockJS('/ws', null, {transports: ['websocket']}); 
        stompClient = Stomp.over(chatSocket);
        stompClient.debug = null; 

        stompClient.connect({}, function (frame) {
            console.log('채팅 서버 연결 성공 및 방 감시 시작 🚀');
            rooms.forEach(room => { subscribeToRoom(room.roomId); });
        }, function (error) {
            console.warn("⚠️ 채팅 소켓 연결 실패. 5초 후 재연결 시도.");
            setTimeout(connectChatServer, 5000);
        });
    }

    connectChatServer();

    // 🔥 [AWS EKS 인프라 패치] 알림 서버 소켓 인그레스 분기망 동기화 규칙 적용
    const notifSocket = new SockJS('/ws-notif', null, {transports: ['websocket']});
    const notifClient = Stomp.over(notifSocket);
    notifClient.debug = null;

    notifClient.connect({}, function (frame) {
        console.log(`%c🔔 알림 서버 연결 성공! 타겟 라우터: /topic/user.${myRealUserId}`, "color: #28a745; font-weight: bold;");
        
        notifClient.subscribe(`/topic/user.${myRealUserId}`, function (response) {
            try {
                const noticeData = JSON.parse(response.body);
                if (!noticeData) return;

                const dataObj = noticeData.payload || noticeData;
                const roomId = dataObj.roomId || dataObj.id || "";
                const msgType = dataObj.type || "";

                if (msgType === "TALK" && document.getElementById(`room-item-${roomId}`)) {
                    if (typeof loadMyChatRooms === 'function') loadMyChatRooms();
                    if (roomId && typeof subscribeToRoom === 'function') subscribeToRoom(roomId);
                    return; 
                }

                const chatMessage = dataObj.message || dataObj.content || "새로운 알림이 도착했습니다.";
                const senderName = dataObj.senderNickname || dataObj.senderName || "시스템";

                let roomTitle = "";
                if (roomId) {
                    const roomItemEl = document.getElementById(`room-item-${roomId}`);
                    if (roomItemEl) {
                        const titleEl = roomItemEl.querySelector('div');
                        if (titleEl) roomTitle = titleEl.innerText.replace("💬", "").trim();
                    }
                }
                if (!roomTitle) roomTitle = dataObj.roomTitle || "새로운 방";

                const senderId = dataObj.sender || dataObj.senderId || null;
                if (senderId && myRealUserId && String(myRealUserId) === String(senderId)) return;

                if (typeof loadMyChatRooms === 'function') loadMyChatRooms();
                if (roomId && typeof subscribeToRoom === 'function') subscribeToRoom(roomId);

                showNotificationToast(`[${roomTitle}] ${senderName}: ${chatMessage}`);
            } catch (parsingError) {
                console.error("❌ 알림 가공 에러:", parsingError);
            }
        });
    }, function (error) { console.error("❌ 알림 소켓 유실:", error); });
} 

function subscribeToRoom(roomId) {
    if (activeSubscriptions[roomId]) return;

    activeSubscriptions[roomId] = stompClient.subscribe(`/sub/chat/room/${roomId}`, function (response) {
        const messageBody = JSON.parse(response.body);
        if (messageBody.type === "READ") return;

        let isMessageFromMe = false;
        if (myRealUserId && messageBody.sender) {
            if (String(myRealUserId) === String(messageBody.sender)) isMessageFromMe = true;
        }

        if (Number(currentRoomId) === Number(roomId)) {
            displayMessage(messageBody);
            
            if (messageBody.type === "ENTER" || messageBody.type === "QUIT") {
                fetch(`/api/chat/rooms/${roomId}/users`, {
                    method: 'GET',
                    headers: { 'Authorization': `Bearer ${token}` }
                })
                .then(res => res.json())
                .then(participants => {
                    const pListDiv = document.getElementById('participant-list');
                    if (pListDiv) {
                        pListDiv.innerHTML = ''; 
                        participants.forEach(p => {
                            const pItem = document.createElement('div');
                            pItem.className = 'participant-item';
                            const isMeMark = (String(myRealUserId) === String(p.userId)) ? ' <span style="font-size:11px; color: var(--primary); font-weight:bold;">(나)</span>' : '';
                            pItem.innerHTML = `<i class="fa-regular fa-user" style="color:var(--text-muted); font-size:12px;"></i> <strong>${p.nickname}</strong>${isMeMark}`;
                            pListDiv.appendChild(pItem);
                        });
                    }
                }).catch(e => console.error(e));
            }

            if (messageBody.messageId) {
                sendWebSocketReadReceipt(roomId, messageBody.messageId);
            }
        } else {
            if (!isMessageFromMe) {
                const senderName = messageBody.senderNickname || `유저(${messageBody.sender})`;
                const chatMessage = messageBody.message || messageBody.content || "";
                
                let roomTitle = "";
                const roomItemEl = document.getElementById(`room-item-${roomId}`);
                if (roomItemEl) {
                    const titleEl = roomItemEl.querySelector('div');
                    if (titleEl) roomTitle = titleEl.innerText.replace("💬", "").trim();
                }
                if (!roomTitle) roomTitle = `${roomId}번 방`;

                showNotificationToast(`[${roomTitle}] ${senderName}: ${chatMessage}`);
            }
        }
        
        if (!isMessageFromMe) {
            if (Number(currentRoomId) === Number(roomId)) {
                unreadCounts[roomId] = 0;
            } else {
                const badgeTarget = document.getElementById(`badge-${roomId}`);
                if (badgeTarget && badgeTarget.style.display !== "none") {
                    unreadCounts[roomId] = Number(badgeTarget.innerText) + 1;
                } else {
                    unreadCounts[roomId] = (unreadCounts[roomId] || 0) + 1;
                }
            }
        } else {
            unreadCounts[roomId] = 0;
        }
        
        const receivedText = messageBody.message || messageBody.content || "";
        realTimeLastMessages[roomId] = receivedText;

        const lastMsgTarget = document.getElementById(`last-msg-${roomId}`);
        if (lastMsgTarget) lastMsgTarget.innerText = receivedText;

        updateBadgeUI(roomId);

        const roomItemEl = document.getElementById(`room-item-${roomId}`);
        const roomListDiv = document.getElementById('room-list');
        if (roomItemEl && roomListDiv) {
            roomListDiv.insertBefore(roomItemEl, roomListDiv.firstChild);
        }
    });
}
    
async function loadMyChatRooms() {
    try {
        const response = await fetch('/api/chat/rooms', {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const rooms = await response.json();
        const roomListDiv = document.getElementById('room-list');
        if (!roomListDiv) return;
        
        roomListDiv.innerHTML = ''; 
        if (!rooms || rooms.length === 0) {
            roomListDiv.innerHTML = '<p style="text-align: center; color: #aaa; margin-top: 40px; font-size:14px;">참여 중인 방이 없습니다.</p>';
            return;
        }

        rooms.sort((a, b) => {
            const timeA = a.lastMessageAt ? new Date(a.lastMessageAt).getTime() : 0;
            const timeB = b.lastMessageAt ? new Date(b.lastMessageAt).getTime() : 0;
            const hasCacheA = realTimeLastMessages[a.roomId] ? 1 : 0;
            const hasCacheB = realTimeLastMessages[b.roomId] ? 1 : 0;
            if (hasCacheA !== hasCacheB) return hasCacheB - hasCacheA;
            return timeB - timeA;
        });
        
        rooms.forEach(room => {
            const roomItem = document.createElement('div');
            roomItem.className = 'room-item';
            roomItem.id = `room-item-${room.roomId}`;
            roomItem.onclick = () => selectChatRoom(room.roomId, room.title); 
            
            const serverUnreadCount = Number(room.unreadCount || 0);
            unreadCounts[room.roomId] = serverUnreadCount;
            if (Number(currentRoomId) === Number(room.roomId)) unreadCounts[room.roomId] = 0;

            const displayLastMessage = realTimeLastMessages[room.roomId] || room.lastMessage || '아직 대화가 없습니다.';
            
            roomItem.innerHTML = `
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <div style="font-weight: 700; color: var(--text-main); font-size:15px;">💬 ${room.title}</div>
                    <div class="badge-el" id="badge-${room.roomId}" style="display: none; background: #ef4444; color: white; border-radius: 20px; padding: 2px 8px; font-size: 11px; font-weight: bold;">0</div>
                </div>
                <div class="last-msg-text" id="last-msg-${room.roomId}" style="font-size: 13px; color: var(--text-muted); margin-top: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                    ${displayLastMessage} 
                </div>
            `;
            roomListDiv.appendChild(roomItem);
            updateBadgeUI(room.roomId);

            if (stompClient !== null && stompClient.connected) subscribeToRoom(room.roomId);
        });

        if (stompClient === null) connectWebSocket(rooms); 
    } catch (error) { console.error('방 목록 로드 실패:', error); }
}
	
function updateBadgeUI(roomId) {
    const badgeTarget = document.getElementById(`badge-${roomId}`);
    if (!badgeTarget) return;
    const count = Number(unreadCounts[roomId] || 0);
    if (count > 0) {
        badgeTarget.innerText = count;
        badgeTarget.style.display = "block"; 
    } else { 
        badgeTarget.innerText = "0";
        badgeTarget.style.display = "none";
    }
}

async function selectChatRoom(roomId, title) {
    currentRoomId = roomId;
    currentChatPage = 0;
    hasNextChatPage = true;
    isChatLoading = false;
    unreadCounts[roomId] = 0;
    updateBadgeUI(roomId);

    const mainBody = document.getElementById('chat-main-body');
    if (!mainBody) return;

    mainBody.innerHTML = `
        <div class="chat-content">
            <div class="chat-header">
                <div>
                    <span style="color: var(--text-main); font-weight:700;">💬 ${title}</span>
                    <span style="font-size: 11px; color: var(--text-muted); margin-left: 6px;">#${roomId}</span>
                </div>
                <div style="display: flex; gap: 6px;">
                    <button class="btn btn-success btn-sm" onclick="openInviteModal()"><i class="fa-solid fa-user-plus"></i> 초대</button>
                    <button class="btn btn-danger btn-sm" onclick="exitChatRoom(${roomId})"><i class="fa-solid fa-door-open"></i> 나가기</button>
                </div>
            </div>
            <div class="message-area" id="message-area"></div>
            <div class="input-area">
                <input type="text" id="message-input" placeholder="메시지를 입력하세요..." onkeydown="if(event.keyCode==13) sendMessage()">
                <button class="btn" onclick="sendMessage()"><i class="fa-solid fa-paper-plane"></i> 전송</button>
            </div>
        </div>
        <div class="participant-sidebar">
            <div class="participant-title">대화 상대방</div>
            <div class="participant-list" id="participant-list">
                <p style="font-size:12px; color: var(--text-muted);">상대방 갱신 중...</p>
            </div>
        </div>
    `;

    await loadPagedChatHistory(roomId, currentChatPage);

    const messageAreaEl = document.getElementById('message-area');
    if (messageAreaEl) {
        messageAreaEl.addEventListener('scroll', function() {
            if (this.scrollTop <= 5 && hasNextChatPage && !isChatLoading) {
                currentChatPage++; 
                loadPagedChatHistory(currentRoomId, currentChatPage);
            }
        });
    }

    try {
        const response = await fetch(`/api/chat/rooms/${roomId}/users`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const participants = await response.json();
        const pListDiv = document.getElementById('participant-list');
        if (pListDiv) {
            pListDiv.innerHTML = ''; 
            participants.forEach(p => {
                const pItem = document.createElement('div');
                pItem.className = 'participant-item';
                const isMeMark = (String(myRealUserId) === String(p.userId)) ? ' <span style="font-size:11px; color: var(--primary); font-weight:bold;">(나)</span>' : '';
                pItem.innerHTML = `<i class="fa-regular fa-user" style="color:var(--text-muted); font-size:12px;"></i> <strong>${p.nickname}</strong>${isMeMark}`;
                pListDiv.appendChild(pItem);
            });
        }
    } catch (error) { console.error(error); }
}

async function loadPagedChatHistory(roomId, page) {
     if (isChatLoading) return;
     isChatLoading = true;

     try {
         const response = await fetch(`/api/chat/rooms/${roomId}/messages?page=${page}&size=${chatPageSize}`, {
             method: 'GET',
             headers: { 'Authorization': `Bearer ${token}` }
         });
         
         if (!response.ok) throw new Error(`서버 응답 에러`);
         const sliceResult = await response.json();
         const messageArea = document.getElementById('message-area');
         if (!messageArea) { isChatLoading = false; return; }

         const savedScrollHeight = messageArea.scrollHeight;
         const histories = sliceResult.content || [];
         hasNextChatPage = !sliceResult.last; 

         histories.forEach(hist => {
             const formattedMsg = {
                 sender: String(hist.sender),         
                 senderNickname: hist.senderNickname,
                 message: hist.message,
                 type: hist.type,
                 createdAt: hist.createdAt 
             };
             const msgNode = createMessageNode(formattedMsg);
             if (page === 0) messageArea.appendChild(msgNode);
             else messageArea.insertBefore(msgNode, messageArea.firstChild);
         });

         if (page === 0) {
             messageArea.scrollTop = messageArea.scrollHeight;
             if (histories.length > 0) {
                 let maxMsgId = 0;
                 histories.forEach(h => {
                     const idCandidate = h.messageId || h.message_id || h.id;
                     if (idCandidate && !isNaN(idCandidate)) {
                         const currentId = Number(idCandidate);
                         if (currentId > maxMsgId) maxMsgId = currentId;
                     }
                 });
                 if (maxMsgId > 0) {
                     sendWebSocketReadReceipt(roomId, maxMsgId);
                     unreadCounts[roomId] = 0;
                     updateBadgeUI(roomId);
                 }
             }
         } else {
             messageArea.scrollTop = messageArea.scrollHeight - savedScrollHeight;
         }
     } catch (error) { console.error("❌ 페이징 이력 로드 실패:", error); } 
     finally { isChatLoading = false; }
 }

function sendWebSocketReadReceipt(roomId, lastMessageId) {
    if (stompClient === null || !stompClient.connected) return;
    const readMessage = {
        type: "READ",
        roomId: String(roomId),
        sender: String(myRealUserId),
        message: String(lastMessageId)
    };
    stompClient.send("/pub/chat/message", {}, JSON.stringify(readMessage));
}

function createMessageNode(message) {
    const msgWrapper = document.createElement('div');
    msgWrapper.style.display = 'flex';
    msgWrapper.style.flexDirection = 'column';
    msgWrapper.style.marginBottom = '8px'; 
    const textContent = message.message || message.content || ""; 

    if (message.type === "ENTER" || message.type === "QUIT") {
        const systemNoticeDiv = document.createElement('div');
        systemNoticeDiv.style.alignSelf = 'center'; 
        systemNoticeDiv.style.background = '#e2e8f0'; 
        systemNoticeDiv.style.color = '#475569'; 
        systemNoticeDiv.style.padding = '6px 16px';
        systemNoticeDiv.style.borderRadius = '20px';
        systemNoticeDiv.style.fontSize = '12px';
        systemNoticeDiv.style.margin = '8px 0';
        systemNoticeDiv.innerHTML = `<i class="fa-solid fa-bullhorn" style="font-size:11px; margin-right:4px;"></i> ${textContent}`;
        msgWrapper.appendChild(systemNoticeDiv);
        return msgWrapper;
    }

    const msgDiv = document.createElement('div');
    const displayName = message.senderNickname || `유저(${message.sender})`;

    let timeHtml = "";
    let finalTimeText = "방금 전";
    
    try {
        if (message.createdAt) {
            const rawTime = message.createdAt.includes("T") ? message.createdAt.split("T")[1] : message.createdAt;
            const timeParts = rawTime.split(":");
            const hour = parseInt(timeParts[0]);
            const min = timeParts[1];
            const ampm = hour >= 12 ? "오후" : "오전";
            const displayHour = hour > 12 ? hour - 12 : (hour === 0 ? 12 : hour);
            finalTimeText = `${ampm} ${displayHour}:${min}`;
        } else {
            const now = new Date();
            let hour = now.getHours();
            const min = String(now.getMinutes()).padStart(2, '0');
            const ampm = hour >= 12 ? "오후" : "오전";
            const displayHour = hour > 12 ? hour - 12 : (hour === 0 ? 12 : hour);
            finalTimeText = `${ampm} ${displayHour}:${min}`;
        }
    } catch (e) {
        const now = new Date();
        let hour = now.getHours();
        const min = String(now.getMinutes()).padStart(2, '0');
        const ampm = hour >= 12 ? "오후" : "오전";
        const displayHour = hour > 12 ? hour - 12 : (hour === 0 ? 12 : hour);
        finalTimeText = `${ampm} ${displayHour}:${min}`;
    }

    timeHtml = `<span class="chat-time" style="font-size: 10px; color: var(--text-muted); margin: 0 6px; align-self: flex-end; white-space: nowrap; display: inline-block;">${finalTimeText}</span>`;

    let isMe = false;
    if (myRealUserId && message.sender) {
        if (String(myRealUserId) === String(message.sender)) isMe = true;
    }

    const bubbleContainer = document.createElement('div');
    bubbleContainer.style.display = 'flex';
    bubbleContainer.style.width = '100%';

    if (isMe) {
        bubbleContainer.style.justifyContent = 'flex-end'; 
        msgDiv.className = 'msg me';
        msgDiv.innerText = textContent; 
        
        bubbleContainer.innerHTML = timeHtml;
        bubbleContainer.appendChild(msgDiv);
    } else {
        bubbleContainer.style.justifyContent = 'flex-start'; 
        msgDiv.className = 'msg other';
        msgDiv.innerHTML = `
            <div class="msg-sender">👤 ${displayName}</div>
            <div>${textContent}</div>
        `;
        
        bubbleContainer.appendChild(msgDiv);
        bubbleContainer.innerHTML += timeHtml;
    }

    msgWrapper.appendChild(bubbleContainer);
    return msgWrapper;
}

function displayMessage(message) {
    const messageArea = document.getElementById('message-area');
    if (!messageArea) return;

    const textContent = message.message || message.content || "";
    if (!textContent) return;

    const msgWrapper = createMessageNode(message);
    messageArea.appendChild(msgWrapper);
    
    const isAtBottom = (messageArea.scrollHeight - messageArea.scrollTop - messageArea.clientHeight) <= 150;
    if (isAtBottom) {
        messageArea.scrollTop = messageArea.scrollHeight; 
    }
}

async function exitChatRoom(roomId) {
    if (!confirm("정말 이 채팅방을 나가시겠습니까?\n나간 방의 대화 이력은 모두 삭제됩니다.")) return;
    try {
        const response = await fetch(`/api/chat/rooms/${roomId}/exit`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (response.ok) {
            alert("채팅방을 정상적으로 나갔습니다.");
            if (activeSubscriptions[roomId]) {
                activeSubscriptions[roomId].unsubscribe();
                delete activeSubscriptions[roomId];
            }
            currentRoomId = null;
            document.getElementById('chat-main-body').innerHTML = `
                <div style="flex: 1; display: flex; flex-direction: column; justify-content: center; align-items: center; color: var(--text-muted); gap: 12px;">
                    <i class="fa-solid fa-message" style="font-size: 48px; color: #cbd5e1;"></i>
                    <h3 style="color: var(--text-main); font-weight: 700;">💬 TalkKing 대시보드</h3>
                    <p style="font-size: 14px;">대화방을 클릭하거나 동료를 초대해 톡을 나눠보세요!</p>
                </div>
            `;
            loadMyChatRooms(); 
        } else { alert("방 나가기에 실패했습니다."); }
    } catch (e) { alert("오류 발생: " + e.message); }
}

function logout() {
    if (!confirm("로그아웃 하시겠습니까?")) return;
    
    // 1. LocalStorage 청소
    localStorage.removeItem('accessToken'); 
    localStorage.removeItem('refreshToken'); // 🔥 리프레시 토큰도 확실히 삭제
    
    // 2. 쿠키 잔재 청소
    document.cookie = "accessToken=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
    document.cookie = "refreshToken=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
    
    alert("로그아웃 되었습니다. 로그인 페이지로 이동합니다.");
    window.location.href = '/login';
}

function sendMessage() {
    const input = document.getElementById('message-input');
    const content = input.value.trim();
    if (!content || !currentRoomId) return;

    const roomItemEl = document.getElementById(`room-item-${currentRoomId}`);
    const roomListDiv = document.getElementById('room-list');
    let previousText = "";
    let lastMsgTarget = null;

    if (roomItemEl) {
        lastMsgTarget = roomItemEl.querySelector('.last-msg-text');
        if (lastMsgTarget) previousText = lastMsgTarget.innerText;
    }

    if (roomItemEl && roomListDiv) {
        realTimeLastMessages[currentRoomId] = content;
        if (lastMsgTarget) lastMsgTarget.innerText = content;
        roomListDiv.insertBefore(roomItemEl, roomListDiv.firstChild);
    }

    try {
        if (!stompClient || !stompClient.connected) throw new Error("웹소켓 유실됨");

        const chatMessage = {
            type: "TALK",
            roomId: String(currentRoomId),
            message: content,
            sender: String(myRealUserId) 
        };

        stompClient.send("/pub/chat/message", {}, JSON.stringify(chatMessage));
        input.value = '';
    } catch (err) {
        console.error("❌ 전송 실패 UI 롤백:", err);
        if (lastMsgTarget) {
            lastMsgTarget.innerText = previousText;
            realTimeLastMessages[currentRoomId] = previousText;
        }
        loadMyChatRooms(); 
        alert("네트워크 연결이 불안정하여 메시지 전송에 실패했습니다.");
    }
}

function openCreateModal() {
    modalMode = "CREATE";
    document.getElementById('modal-title').innerHTML = "<i class='fa-solid fa-plus-circle' style='color:var(--primary); margin-right:6px;'></i>새 그룹 채팅방 개설";
    document.getElementById('new-room-title').style.display = "block";
    document.getElementById('modal-submit-btn').innerText = "방 생성 및 초대";
    document.getElementById('user-action-modal').style.display = 'flex';
}
function openInviteModal() {
    modalMode = "INVITE";
    document.getElementById('modal-title').innerHTML = "<i class='fa-solid fa-user-plus' style='color:#10b981; margin-right:6px;'></i>멤버 초대하기";
    document.getElementById('new-room-title').style.display = "none";
    document.getElementById('modal-submit-btn').innerText = "현재 방에 추가 초대";
    document.getElementById('user-action-modal').style.display = 'flex';
}
function closeModal() { 
    document.getElementById('user-action-modal').style.display = 'none'; 
    document.getElementById('search-results').innerHTML = '<p style="text-align: center; color: #aaa; margin-top: 30px; font-size:13px;">검색어를 입력해 주세요.</p>';
    document.getElementById('new-room-title').value = '';
    document.getElementById('user-search-keyword').value = '';
}
async function searchUsers() {
    const keyword = document.getElementById('user-search-keyword').value;
    if (!keyword) return alert('검색어를 입력하세요.');
    try {
        const response = await fetch(`/api/chat/rooms/search?keyword=${encodeURIComponent(keyword)}`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const users = await response.json();
        const resultBox = document.getElementById('search-results');
        if (!resultBox) return;
        resultBox.innerHTML = '';
        if (users.length === 0) {
            resultBox.innerHTML = '<p style="text-align: center; color: #aaa; margin-top: 30px; font-size:13px;">검색 결과가 없습니다.</p>';
            return;
        }
        users.forEach(user => {
            const item = document.createElement('div');
            item.className = 'search-item';
            item.innerHTML = `<div><strong>${user.nickname}</strong> <span style="font-size: 12px; color: var(--text-muted);">(@${user.username})</span></div><input type="checkbox" value="${user.userId}" class="user-checkbox">`;
            resultBox.appendChild(item);
        });
    } catch (error) { alert('유저 검색 에러: ' + error.message); }
}
function submitModalAction() {
    if (modalMode === "CREATE") createGroupRoom();
    else if (modalMode === "INVITE") inviteGroupMembers();
}
async function createGroupRoom() {
    const roomTitle = document.getElementById('new-room-title').value;
    if (!roomTitle) return alert('채팅방 이름을 입력하세요.');
    const checkboxes = document.querySelectorAll('.user-checkbox:checked');
    const invitedUserIds = Array.from(checkboxes).map(cb => parseInt(cb.value));
    if (invitedUserIds.length === 0) return alert('최소 한 명 이상의 유저를 선택해야 합니다.');
    try {
        const response = await fetch('/api/chat/rooms', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ roomTitle, invitedUserIds })
        });
        if (response.ok) {
            alert(`방 생성 성공!`);
            closeModal();
            loadMyChatRooms();
        }
    } catch (error) { alert('방 생성 오류: ' + error.message); }
}

async function inviteGroupMembers() {
    const checkboxes = document.querySelectorAll('.user-checkbox:checked');
    const invitedUserIds = Array.from(checkboxes).map(cb => parseInt(cb.value));
    if (invitedUserIds.length === 0) return alert('추가로 초대할 유저를 선택해 주세요.');
    try {
        const response = await fetch(`/api/chat/rooms/${currentRoomId}/invite`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ invitedUserIds })
        });
        if (response.ok) {
            alert("선택한 유저가 방에 정상 초대되었습니다!");
            closeModal();
            
            fetch(`/api/chat/rooms/${currentRoomId}/users`, {
                method: 'GET',
                headers: { 'Authorization': `Bearer ${token}` }
            })
            .then(res => res.json())
            .then(participants => {
                const pListDiv = document.getElementById('participant-list');
                if (pListDiv) {
                    pListDiv.innerHTML = ''; 
                    participants.forEach(p => {
                        const pItem = document.createElement('div');
                        pItem.className = 'participant-item';
                        const isMeMark = (String(myRealUserId) === String(p.userId)) ? ' <span style="font-size:11px; color:var(--primary); font-weight:bold;">(나)</span>' : '';
                        pItem.innerHTML = `<i class="fa-regular fa-user" style="color:var(--text-muted); font-size:12px;"></i> <strong>${p.nickname}</strong>${isMeMark}`;
                        pListDiv.appendChild(pItem);
                    });
                }
            }).catch(e => console.error(e));
        } else { alert("초대에 실패했습니다."); }
    } catch (error) { alert('초대 오류: ' + error.message); }
}

function showNotificationToast(message) {
    console.log("📢 [알림 엔진 가동 날것 데이터]:", message);

    let cleanMessage = message.replace(/[\r\n]+/g, " ").trim();
    let osTitle = "💬 TalkKing 새 메시지";
    let osBody = cleanMessage; 
    let roomTag = "talkking-chat-room"; 
    let displayMessage = cleanMessage; 

    try {
        if (cleanMessage.startsWith("[")) {
            const bracketMatch = cleanMessage.match(/^\[([^\]]+)\]/);
            if (bracketMatch && bracketMatch[1]) {
                const originalRoomName = bracketMatch[1].trim();
                const fixedRoomName = originalRoomName.replace(/\s+\d+$/, "").trim();
                osTitle = `💬 [${fixedRoomName}] 새 메시지`;
                displayMessage = cleanMessage.replace(`[${originalRoomName}]`, `[${fixedRoomName}]`);
                osBody = displayMessage; 
                roomTag = `room-${fixedRoomName}`; 
            }
        } 
        else if (cleanMessage.includes("'")) {
            const quoteMatch = cleanMessage.match(/'([^']+)'/);
            if (quoteMatch && quoteMatch[1]) {
                const originalRoomName = quoteMatch[1].trim();
                const fixedRoomName = originalRoomName.replace(/\s+\d+$/, "").trim();
                osTitle = `📢 [${fixedRoomName}] 초대 알림`;
                displayMessage = cleanMessage.replace(`'${originalRoomName}'`, `'${fixedRoomName}'`);
                osBody = displayMessage;
                roomTag = `invite-${fixedRoomName}`;
            }
        }
    } catch (e) { 
        console.error("알림 방이름 파싱 실패:", e); 
    }

    function triggerOSNotification() {
        try {
            new Notification(osTitle, {
                body: osBody,             
                icon: "/image/talkking.png", 
                tag: roomTag,             
                renotify: true
            });
        } catch (osError) {
            console.error("❌ OS Notification 생성 중 실패:", osError);
        }
    }

    if (Notification.permission === "granted") {
        triggerOSNotification();
    } else if (Notification.permission !== "denied") {
        Notification.requestPermission().then(permission => {
            if (permission === "granted") {
                triggerOSNotification();
            }
        });
    }

    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = 'toast-notification';
    
    toast.innerHTML = `
        <span style="font-size: 16px;">💬</span>
        <div style="flex: 1;">
            <div style="font-weight: 700; margin-bottom: 2px; color: #6366f1; font-size:13px;">새 메시지</div>
            <div style="color: #f1f5f9; line-height: 1.4; word-break: break-all; font-size:13px;">${displayMessage}</div>
        </div>
    `;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.transition = 'all 0.4s cubic-bezier(0.16, 1, 0.3, 1)';
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-20px)';
        setTimeout(() => { if (toast.parentNode) container.removeChild(toast); }, 400);
    }, 4000);
}
if (window.Notification) Notification.requestPermission();