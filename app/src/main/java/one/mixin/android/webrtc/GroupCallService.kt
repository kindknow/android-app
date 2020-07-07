package one.mixin.android.webrtc

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import io.reactivex.disposables.Disposable
import one.mixin.android.Constants
import one.mixin.android.Constants.SLEEP_MILLIS
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.api.SignalKey
import one.mixin.android.api.createPreKeyBundle
import one.mixin.android.api.response.UserSession
import one.mixin.android.api.service.ConversationService
import one.mixin.android.db.ParticipantDao
import one.mixin.android.db.ParticipantSessionDao
import one.mixin.android.db.findFullNameById
import one.mixin.android.db.insertAndNotifyConversation
import one.mixin.android.event.CallEvent
import one.mixin.android.event.SenderKeyChange
import one.mixin.android.extension.base64Encode
import one.mixin.android.extension.decodeBase64
import one.mixin.android.extension.fromJson
import one.mixin.android.extension.getDeviceId
import one.mixin.android.extension.mainThread
import one.mixin.android.extension.networkConnected
import one.mixin.android.extension.nowInUtc
import one.mixin.android.extension.toast
import one.mixin.android.job.MessageResult
import one.mixin.android.job.MixinJob
import one.mixin.android.ui.call.CallActivity
import one.mixin.android.util.ErrorHandler
import one.mixin.android.util.Session
import one.mixin.android.vo.CallType
import one.mixin.android.vo.KrakenData
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.Participant
import one.mixin.android.vo.ParticipantSession
import one.mixin.android.vo.Sdp
import one.mixin.android.vo.SenderKeyStatus
import one.mixin.android.vo.createCallMessage
import one.mixin.android.vo.generateConversationChecksum
import one.mixin.android.vo.isGroupCallType
import one.mixin.android.websocket.BlazeMessage
import one.mixin.android.websocket.BlazeMessageData
import one.mixin.android.websocket.BlazeMessageParam
import one.mixin.android.websocket.BlazeMessageParamSession
import one.mixin.android.websocket.BlazeSignalKeyMessage
import one.mixin.android.websocket.ChatWebSocket
import one.mixin.android.websocket.LIST_KRAKEN_PEERS
import one.mixin.android.websocket.createBlazeSignalKeyMessage
import one.mixin.android.websocket.createConsumeSessionSignalKeys
import one.mixin.android.websocket.createConsumeSignalKeysParam
import one.mixin.android.websocket.createKrakenMessage
import one.mixin.android.websocket.createListKrakenPeers
import one.mixin.android.websocket.createSignalKeyMessage
import one.mixin.android.websocket.createSignalKeyMessageParam
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GroupCallService : CallService() {

    private val scheduledExecutors = Executors.newScheduledThreadPool(1)
    private val scheduledFutures = mutableMapOf<String, ScheduledFuture<*>>()

    @Inject
    lateinit var chatWebSocket: ChatWebSocket
    @Inject
    lateinit var participantSessionDao: ParticipantSessionDao
    @Inject
    lateinit var participantDao: ParticipantDao
    @Inject
    lateinit var conversationApi: ConversationService

    private var disposable: Disposable? = null

    override fun handleIntent(intent: Intent): Boolean {
        var handled = true
        when (intent.action) {
            ACTION_KRAKEN_PUBLISH -> handlePublish(intent)
            ACTION_KRAKEN_RECEIVE_PUBLISH -> handleReceivePublish(intent)
            ACTION_KRAKEN_RECEIVE_INVITE -> handleReceiveInvite(intent)
            ACTION_KRAKEN_RECEIVE_END -> handleReceiveEnd(intent)
            ACTION_KRAKEN_RECEIVE_CANCEL -> handleReceiveCancel(intent)
            ACTION_KRAKEN_ACCEPT_INVITE -> handleAcceptInvite()
            ACTION_KRAKEN_END -> handleKrakenEnd()
            ACTION_KRAKEN_CANCEL -> handleKrakenCancel()
            ACTION_KRAKEN_DECLINE -> handleKrakenDecline()
            ACTION_KRAKEN_CANCEL_SILENTLY -> handleCallLocalFailed()
            else -> handled = false
        }
        return handled
    }

    private fun handleReceivePublish(intent: Intent) {
        val blazeMessageData = intent.getSerializableExtra(EXTRA_BLAZE) as? BlazeMessageData
        requireNotNull(blazeMessageData)
        val cid = blazeMessageData.conversationId
        val userId = blazeMessageData.userId
        callState.addUser(cid, userId)
        startCheckPeers(cid)

        if (callState.isNotIdle() && callState.conversationId != cid) return

        val krakenDataString = String(blazeMessageData.data.decodeBase64())
        if (krakenDataString == PUBLISH_PLACEHOLDER) {
            val trackId = if (callState.isGroupCall()) callState.trackId else null
            if (!trackId.isNullOrEmpty()) {
                sendSubscribe(cid, trackId)
            }
        }
    }

    private fun handlePublish(intent: Intent) {
        Timber.d("@@@ handlePublish")
        if (callState.state == CallState.STATE_DIALING) return

        if (isDisconnected.compareAndSet(true, false)) {
            callState.state = CallState.STATE_DIALING
            callState.callType = CallType.Group
            updateForegroundNotification()
            val cid = intent.getStringExtra(EXTRA_CONVERSATION_ID)
            requireNotNull(cid)
            callState.conversationId = cid
            val users = intent.getStringArrayListExtra(EXTRA_USERS)
            users?.let { callState.setPendingUsers(cid, it) }
            callState.isOffer = true
            timeoutFuture = timeoutExecutor.schedule(TimeoutRunnable(), DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            CallActivity.show(this)
            audioManager.start(true)
            publish(cid)
        }
    }

    @SuppressLint("AutoDispose")
    private fun publish(conversationId: String) {
        Timber.d("@@@ publish")
        if (callState.isIdle()) return

        callState.addUser(conversationId, self.userId)
        getTurnServer { turns ->
            checkSessionSenderKey(conversationId)
            val key = signalProtocol.getSenderKeyPublic(conversationId, Session.getAccountId()!!)
            peerConnectionClient.createOffer(
                turns,
                setLocalSuccess = {
                    val blazeMessageParam = BlazeMessageParam(
                        conversation_id = conversationId,
                        category = MessageCategory.KRAKEN_PUBLISH.name,
                        message_id = UUID.randomUUID().toString(),
                        jsep = gson.toJson(Sdp(it.description, it.type.canonicalForm())).base64Encode()
                    )
                    val bm = createKrakenMessage(blazeMessageParam)
                    val data = getBlazeMessageData(bm) ?: return@createOffer
                    val krakenData = gson.fromJson(String(data.data.decodeBase64()), KrakenData::class.java)
                    subscribe(krakenData, conversationId)
                },
                frameKey = key
            )

            disposable = RxBus.listen(SenderKeyChange::class.java)
                .subscribe { event ->
                    if (event.conversationId != conversationId) {
                        return@subscribe
                    }
                    if (event.userId != null && event.sessionId != null) {
                        val users = callState.getUsersByConversationId(event.conversationId) ?: return@subscribe
                        if (users.contains(event.userId)) {
                            val frameKey = getSenderPublicKey(event.userId, event.sessionId) ?: return@subscribe
                            peerConnectionClient.setReceiverFrameKey(event.userId, event.sessionId, frameKey)
                        }
                    } else {
                        checkSessionSenderKey(conversationId)
                        val frameKey = signalProtocol.getSenderKeyPublic(conversationId, Session.getAccountId()!!)
                        peerConnectionClient.setSenderFrameKey(frameKey)
                    }
                }
        }
    }

    private fun subscribe(data: KrakenData, conversationId: String) {
        if (callState.isIdle()) return

        Timber.d("@@@ subscribe ${data.getSessionDescription().type == SessionDescription.Type.ANSWER}")
        if (data.getSessionDescription().type == SessionDescription.Type.ANSWER) {
            peerConnectionClient.setAnswerSdp(data.getSessionDescription())
            callState.trackId = data.trackId
            sendSubscribe(conversationId, data.trackId)
            startCheckPeers(conversationId)
        }
    }

    private fun sendSubscribe(conversationId: String, trackId: String) {
        Timber.d("@@@ sendSubscribe")
        if (callState.isIdle()) return

        val blazeMessageParam = BlazeMessageParam(
            conversation_id = conversationId, category = MessageCategory.KRAKEN_SUBSCRIBE.name,
            message_id = UUID.randomUUID().toString(), track_id = trackId
        )
        val bm = createKrakenMessage(blazeMessageParam)
        Timber.d("@@@ subscribe track id: $trackId")
        val bmData = getBlazeMessageData(bm) ?: return
        val krakenData = gson.fromJson(String(bmData.data.decodeBase64()), KrakenData::class.java)
        answer(krakenData, conversationId)
    }

    private fun answer(krakenData: KrakenData, conversationId: String) {
        if (callState.isIdle()) return

        Timber.d("@@@ answer ${krakenData.getSessionDescription().type == SessionDescription.Type.OFFER}")
        if (krakenData.getSessionDescription().type == SessionDescription.Type.OFFER) {
            peerConnectionClient.createAnswer(
                krakenData.getSessionDescription(),
                setLocalSuccess = {
                    val blazeMessageParam = BlazeMessageParam(
                        conversation_id = conversationId,
                        category = MessageCategory.KRAKEN_ANSWER.name,
                        message_id = UUID.randomUUID().toString(),
                        jsep = gson.toJson(Sdp(it.description, it.type.canonicalForm())).base64Encode(),
                        track_id = krakenData.trackId
                    )
                    val bm = createKrakenMessage(blazeMessageParam)
                    val data = webSocketChannel(bm) ?: return@createAnswer
                    Timber.d("@@@ answer data: $data")
                }
            )
        }
    }

    private fun startCheckPeers(cid: String) {
        callState.addGroupCallState(cid)
        val existsFuture = scheduledFutures[cid]
        if (existsFuture == null) {
            if (scheduledExecutors.isShutdown) return

            scheduledFutures[cid] = scheduledExecutors.scheduleAtFixedRate(
                ListRunnable(cid),
                KRAKEN_LIST_INTERVAL, KRAKEN_LIST_INTERVAL, TimeUnit.SECONDS
            )
        }
    }

    private fun getPeers(conversationId: String) {
        val blazeMessageParam = BlazeMessageParam(
            conversation_id = conversationId,
            category = MessageCategory.KRAKEN_LIST.name,
            message_id = UUID.randomUUID().toString()
        )
        val bm = createListKrakenPeers(blazeMessageParam)
        val json = getJsonElement(bm) ?: return
        val peerList = gson.fromJson(json, PeerList::class.java)
        Timber.d("@@@ getPeers : ${peerList.peers}")
        if (peerList.peers.isNullOrEmpty()) {
            checkSchedules(conversationId)
            return
        }
        val userIdList = arrayListOf<String>()
        peerList.peers.mapTo(userIdList) { it.userId }
        val currentList = callState.getUsersByConversationId(conversationId)
        if (currentList != null && currentList.size > userIdList.size) {
            if (userIdList.isEmpty()) {
                checkSchedules(conversationId)
            } else {
                callState.setUsersByConversationId(
                    conversationId,
                    userIdList
                )
            }
        }
    }

    private fun handleReceiveInvite(intent: Intent) {
        Timber.d("@@@ handleReceiveInvite")
        val cid = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        requireNotNull(cid)
        val userId = intent.getStringExtra(EXTRA_USER_ID)

        if (callState.isBusy(this)) {
            // TODO send a kraken busy message?
            Timber.d("@@@ receive a invite from $userId in $cid")
            userId?.let {
                saveMessage(cid, it, MessageCategory.KRAKEN_INVITE.name)
            }
            return
        }

        if (isDisconnected.compareAndSet(true, false)) {
            callState.state = CallState.STATE_RINGING
            callState.callType = CallType.Group
            updateForegroundNotification()
            callState.conversationId = cid
            userId?.let {
                callState.setInviter(cid, it)
                callState.addPendingUsers(cid, arrayListOf(it))
            }
            callState.isOffer = false
            timeoutFuture = timeoutExecutor.schedule(TimeoutRunnable(), DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            val playRing = intent.getBooleanExtra(EXTRA_PLAY_RING, true)
            CallActivity.show(this, !playRing)
            if (playRing) {
                audioManager.start(false)
            }
            startCheckPeers(cid)

            userId?.let {
                saveMessage(cid, it, MessageCategory.KRAKEN_INVITE.name)
            }
        }
    }

    private fun handleReceiveEnd(intent: Intent) {
        val cid = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        requireNotNull(cid)
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        requireNotNull(userId)

        callState.removeUser(cid, userId)
        checkConversationUserCount(cid)
    }

    private fun handleReceiveCancel(intent: Intent) {
        val cid = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        requireNotNull(cid)
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        requireNotNull(userId)

        callState.removeUser(cid, userId)
        saveMessage(cid, userId, MessageCategory.KRAKEN_CANCEL.name)
        val fullName = database.findFullNameById(userId)
        mainThread {
            toast(getString(R.string.chat_group_call_cancel, fullName))
        }
        if (callState.isBeforeAnswering()) {
            disconnect()
            checkConversationUserCount(cid)
        }
    }

    private fun handleAcceptInvite() {
        Timber.d("@@@ handleAcceptInvite")
        val cid = callState.conversationId
        if (cid == null) {
            Timber.e("try accept invite but conversation id is null")
            disconnect()
            return
        }
        if (callState.isAnswering()) return

        callState.state = CallState.STATE_ANSWERING
        callState.callType = CallType.Group
        updateForegroundNotification()
        audioManager.stop()
        publish(cid)
    }

    private fun handleKrakenEnd() {
        Timber.d("@@@ handleKrakenEnd")
        if (callState.isIdle()) return

        val cid = callState.conversationId
        val trackId = callState.trackId
        if (cid == null || trackId == null) {
            Timber.e("try send kraken end message but conversation id is $cid, trackId is $trackId")
            disconnect()
            cid?.let { checkConversationUserCount(it) }
            return
        }

        val duration = System.currentTimeMillis() - (callState.connectedTime ?: 0)
        val blazeMessageParam = BlazeMessageParam(
            conversation_id = cid,
            category = MessageCategory.KRAKEN_END.name,
            message_id = UUID.randomUUID().toString(),
            track_id = trackId
        )

        disconnect()

        saveMessage(cid, self.userId, MessageCategory.KRAKEN_END.name, duration.toString())
        val bm = createKrakenMessage(blazeMessageParam)
        val bmData = getBlazeMessageData(bm)

        checkConversationUserCount(cid)
    }

    private fun handleKrakenCancel() {
        Timber.d("@@@ handleKrakenCancel")
        if (callState.isIdle()) return

        val cid = callState.conversationId
        if (cid == null) {
            Timber.e("try send kraken cancel message but conversation id is $cid")
            disconnect()
            cid?.let { checkConversationUserCount(it) }
            return
        }

        val blazeMessageParam = BlazeMessageParam(
            conversation_id = cid,
            category = MessageCategory.KRAKEN_CANCEL.name,
            message_id = UUID.randomUUID().toString()
        )

        disconnect()

        saveMessage(cid, self.userId, MessageCategory.KRAKEN_CANCEL.name)
        val bm = createKrakenMessage(blazeMessageParam)
        val bmData = getBlazeMessageData(bm)

        checkConversationUserCount(cid)
    }

    private fun handleKrakenDecline() {
        Timber.d("@@@ handleKrakenDecline")
        if (callState.isIdle()) return

        val cid = callState.conversationId
        if (cid == null) {
            Timber.e("try send kraken decline message but conversation id is $cid")
            disconnect()
            return
        }

        val inviter = callState.getInviter(cid)

        disconnect()

        saveMessage(cid, self.userId, MessageCategory.KRAKEN_DECLINE.name)
        if (inviter != null) {
            val blazeMessageParam = BlazeMessageParam(
                conversation_id = cid,
                recipient_id = inviter,
                category = MessageCategory.KRAKEN_DECLINE.name,
                message_id = UUID.randomUUID().toString()
            )
            val bm = createKrakenMessage(blazeMessageParam)
            val bmData = getBlazeMessageData(bm) ?: return
            val krakenData = gson.fromJson(String(bmData.data.decodeBase64()), KrakenData::class.java)
        } else {
            Timber.w("try send kraken decline message but inviter is null, conversationId: $cid")
        }

        checkConversationUserCount(cid)
    }

    private fun checkConversationUserCount(conversationId: String) {
        val count = callState.getUsersCountByConversationId(conversationId)
        if (count == 0) {
            checkSchedules(conversationId)
        }
    }

    private fun handleCallLocalFailed() {
        Timber.d("@@@ handleCallLocalFailed")
        if (callState.isIdle()) return

        val cid = callState.conversationId
        disconnect()
        cid?.let { checkConversationUserCount(cid) }
    }

    override fun onPeerConnectionError(description: String) {
        callExecutor.execute { handleKrakenEnd() }
    }

    override fun onTimeout() {
        callExecutor.execute {
            handleKrakenCancel()
        }
    }

    override fun onTurnServerError() {
        handleCallLocalFailed()
    }

    override fun onDisconnected() {
        callExecutor.execute {
            handleKrakenEnd()
        }
    }

    override fun getSenderPublicKey(userId: String, sessionId: String): ByteArray? {
        callState.conversationId?.let {
            return signalProtocol.getSenderKeyPublic(it, userId, sessionId)
        }
        return null
    }

    override fun onCallDisconnected() {
        disposable?.dispose()
    }

    override fun onDestroyed() {
        if (!scheduledExecutors.isShutdown) {
            scheduledExecutors.shutdownNow()
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        callExecutor.execute {
            val trackId = callState.trackId
            val cid = callState.conversationId
            if (trackId != null && cid != null) {
                val blazeMessageParam = BlazeMessageParam(
                    conversation_id = cid,
                    category = MessageCategory.KRAKEN_TRICKLE.name,
                    message_id = UUID.randomUUID().toString(),
                    candidate = gson.toJson(candidate).base64Encode(),
                    track_id = trackId
                )
                val bm = createKrakenMessage(blazeMessageParam)
                val data = webSocketChannel(bm)
            }
        }
    }

    inner class ListRunnable(private val conversationId: String) : Runnable {
        override fun run() {
            getPeers(conversationId)
        }
    }

    private fun getBlazeMessageData(blazeMessage: BlazeMessage): BlazeMessageData? {
        val bm = webSocketChannel(blazeMessage)
        return if (bm != null) {
            gson.fromJson(bm.data, BlazeMessageData::class.java)
        } else null
    }

    private fun getJsonElement(blazeMessage: BlazeMessage): JsonElement? =
        webSocketChannel(blazeMessage)?.data

    private tailrec fun webSocketChannel(blazeMessage: BlazeMessage): BlazeMessage? {
        if (!networkConnected()) {
            Timber.d("@@@ network not connected, action: ${blazeMessage.action}")
            if (blazeMessage.action == LIST_KRAKEN_PEERS) return null

            SystemClock.sleep(SLEEP_MILLIS)
            return blazeMessage
        }

        blazeMessage.params?.conversation_id?.let {
            blazeMessage.params.conversation_checksum = getCheckSum(it)
        }
        val bm = chatWebSocket.sendMessage(blazeMessage)
        Timber.d("@@@ webSocketChannel $blazeMessage, bm: $bm")
        if (bm == null) {
            SystemClock.sleep(SLEEP_MILLIS)
            blazeMessage.id = UUID.randomUUID().toString()
            return webSocketChannel(blazeMessage)
        } else if (bm.error != null) {
            if (bm.error.status == 500) {
                // TODO more check
                when (val errCode = bm.error.code) {
                    7000 -> {
                        Timber.w("try send a ${blazeMessage.action} message,but the remote track has been released.")
                    }
                    ERROR_ROOM_FULL -> {
                        val cid = blazeMessage.params?.conversation_id
                        Timber.d("try to publish and join a group call, but the room is full, conversation id: $cid.")
                        cid?.let {
                            RxBus.publish(CallEvent(it))
                        }
                    }
                    else -> {
                        Timber.w("send blazeMessage by webSocket get a server internal error, code: $errCode")
                    }
                }
                disconnect()
                return null
            }

            return when (bm.error.code) {
                ErrorHandler.CONVERSATION_CHECKSUM_INVALID_ERROR -> {
                    blazeMessage.params?.conversation_id?.let {
                        syncConversation(it)
                        // send sender key
                        checkSessionSenderKey(it)
                    }
                    blazeMessage.id = UUID.randomUUID().toString()
                    webSocketChannel(blazeMessage)
                }
                ErrorHandler.FORBIDDEN -> {
                    null
                }
                else -> {
                    SystemClock.sleep(Constants.SLEEP_MILLIS)
                    blazeMessage.id = UUID.randomUUID().toString()
                    webSocketChannel(blazeMessage)
                }
            }
        }
        return bm
    }

    private fun checkSchedules(conversationId: String) {
        callState.removeGroupCallState(conversationId)
        val listFuture = scheduledFutures.remove(conversationId)
        listFuture?.cancel(true)

        Timber.d("@@@ scheduledFutures isEmpty: ${scheduledFutures.isEmpty()}, isIdel: ${callState.isIdle()}")
        if (scheduledFutures.isEmpty() && callState.isIdle()) {
            disconnect()
            stopSelf()
        }
    }

    private fun checkSessionSenderKey(conversationId: String) {
        val participants = participantSessionDao.getNotSendSessionParticipants(conversationId, Session.getSessionId()!!)
        if (participants.isEmpty()) return
        val requestSignalKeyUsers = arrayListOf<BlazeMessageParamSession>()
        val signalKeyMessages = arrayListOf<BlazeSignalKeyMessage>()
        for (p in participants) {
            if (!signalProtocol.containsSession(p.userId, p.sessionId.getDeviceId())) {
                requestSignalKeyUsers.add(BlazeMessageParamSession(p.userId, p.sessionId))
            } else {
                val (cipherText, err) = signalProtocol.encryptSenderKey(conversationId, p.userId, p.sessionId.getDeviceId())
                if (err) {
                    requestSignalKeyUsers.add(BlazeMessageParamSession(p.userId, p.sessionId))
                } else {
                    signalKeyMessages.add(createBlazeSignalKeyMessage(p.userId, cipherText!!, p.sessionId))
                }
            }
        }

        if (requestSignalKeyUsers.isNotEmpty()) {
            val blazeMessage = createConsumeSessionSignalKeys(createConsumeSignalKeysParam(requestSignalKeyUsers))
            val data = getJsonElement(blazeMessage)
            if (data != null) {
                val signalKeys = Gson().fromJson<ArrayList<SignalKey>>(data)
                val keys = arrayListOf<BlazeMessageParamSession>()
                if (signalKeys.isNotEmpty()) {
                    for (key in signalKeys) {
                        val preKeyBundle = createPreKeyBundle(key)
                        signalProtocol.processSession(key.userId!!, preKeyBundle)
                        val (cipherText, _) = signalProtocol.encryptSenderKey(conversationId, key.userId, preKeyBundle.deviceId)
                        signalKeyMessages.add(createBlazeSignalKeyMessage(key.userId, cipherText!!, key.sessionId))
                        keys.add(BlazeMessageParamSession(key.userId, key.sessionId))
                    }
                } else {
                    Log.e(MixinJob.TAG, "No any group signal key from server: " + requestSignalKeyUsers.toString())
                }

                val noKeyList = requestSignalKeyUsers.filter { !keys.contains(it) }
                if (noKeyList.isNotEmpty()) {
                    val sentSenderKeys = noKeyList.map {
                        ParticipantSession(conversationId, it.user_id, it.session_id!!, SenderKeyStatus.UNKNOWN.ordinal)
                    }
                    participantSessionDao.updateList(sentSenderKeys)
                }
            }
        }
        if (signalKeyMessages.isEmpty()) {
            return
        }
        val checksum = getCheckSum(conversationId)
        val bm = createSignalKeyMessage(createSignalKeyMessageParam(conversationId, signalKeyMessages, checksum))
        val result = deliverNoThrow(bm)
        if (result.retry) {
            return checkSessionSenderKey(conversationId)
        }
        if (result.success) {
            val sentSenderKeys = signalKeyMessages.map {
                ParticipantSession(conversationId, it.recipient_id, it.sessionId!!, SenderKeyStatus.SENT.ordinal)
            }
            participantSessionDao.updateList(sentSenderKeys)
        }
    }

    private tailrec fun deliverNoThrow(blazeMessage: BlazeMessage): MessageResult {
        val bm = chatWebSocket.sendMessage(blazeMessage)
        if (bm == null) {
            SystemClock.sleep(SLEEP_MILLIS)
            return deliverNoThrow(blazeMessage)
        } else if (bm.error != null) {
            return if (bm.error.code == ErrorHandler.CONVERSATION_CHECKSUM_INVALID_ERROR) {
                blazeMessage.params?.conversation_id?.let {
                    syncConversation(it)
                }
                MessageResult(false, retry = true)
            } else if (bm.error.code == ErrorHandler.FORBIDDEN) {
                MessageResult(true, retry = false)
            } else {
                SystemClock.sleep(SLEEP_MILLIS)
                // warning: may caused job leak if server return error data and come to this branch
                return deliverNoThrow(blazeMessage)
            }
        } else {
            return MessageResult(true, retry = false)
        }
    }

    private fun getCheckSum(conversationId: String): String {
        val sessions = participantSessionDao.getParticipantSessionsByConversationId(conversationId)
        return if (sessions.isEmpty()) {
            ""
        } else {
            generateConversationChecksum(sessions)
        }
    }

    private fun syncConversation(conversationId: String) {
        val response = conversationApi.getConversation(conversationId).execute().body()
        if (response != null && response.isSuccess) {
            response.data?.let { data ->
                val remote = data.participants.map {
                    Participant(conversationId, it.userId, it.role, it.createdAt!!)
                }
                participantDao.replaceAll(conversationId, remote)

                data.participantSessions?.let {
                    syncParticipantSession(conversationId, it)
                }
            }
        }
    }

    private fun syncParticipantSession(conversationId: String, data: List<UserSession>) {
        participantSessionDao.deleteByStatus(conversationId)
        val remote = data.map {
            ParticipantSession(conversationId, it.userId, it.sessionId)
        }
        if (remote.isEmpty()) {
            participantSessionDao.deleteByConversationId(conversationId)
            return
        }
        val local = participantSessionDao.getParticipantSessionsByConversationId(conversationId)
        if (local.isEmpty()) {
            participantSessionDao.insertList(remote)
            return
        }
        val common = remote.intersect(local)
        val remove = local.minus(common)
        val add = remote.minus(common)
        if (remove.isNotEmpty()) {
            participantSessionDao.deleteList(remove)
        }
        if (add.isNotEmpty()) {
            participantSessionDao.insertList(add)
        }
    }

    private fun saveMessage(cid: String, userId: String, category: String, duration: String? = null) {
        if (!category.isGroupCallType()) return

        val message = createCallMessage(
            UUID.randomUUID().toString(), cid, userId, category,
            "", nowInUtc(), MessageStatus.READ.name,
            mediaDuration = duration
        )
        database.insertAndNotifyConversation(message)
    }

    companion object {
        private const val KRAKEN_LIST_INTERVAL = 30L
    }
}

private const val ACTION_KRAKEN_PUBLISH = "kraken_publish"
private const val ACTION_KRAKEN_RECEIVE_PUBLISH = "kraken_receive_publish"
private const val ACTION_KRAKEN_RECEIVE_INVITE = "kraken_receive_invite"
const val ACTION_KRAKEN_ACCEPT_INVITE = "kraken_accept_invite"
private const val ACTION_KRAKEN_RECEIVE_END = "kraken_receive_end"
private const val ACTION_KRAKEN_RECEIVE_CANCEL = "kraken_receive_cancel"
const val ACTION_KRAKEN_END = "kraken_end"
const val ACTION_KRAKEN_CANCEL = "kraken_cancel"
const val ACTION_KRAKEN_DECLINE = "kraken_decline"
const val ACTION_KRAKEN_CANCEL_SILENTLY = "kraken_local_cancel"

private const val EXTRA_PLAY_RING = "extra_play_ring"

const val PUBLISH_PLACEHOLDER = "PLACEHOLDER"
const val ERROR_ROOM_FULL = 5002000

data class PeerList(
    val peers: ArrayList<UserSession>?
)

fun publish(ctx: Context, conversationId: String, users: ArrayList<String>? = null) =
    startService<GroupCallService>(ctx, ACTION_KRAKEN_PUBLISH, true) {
        it.putExtra(EXTRA_CONVERSATION_ID, conversationId)
        it.putExtra(EXTRA_USERS, users)
    }

fun receivePublish(ctx: Context, data: BlazeMessageData) =
    startService<GroupCallService>(ctx, ACTION_KRAKEN_RECEIVE_PUBLISH) {
        it.putExtra(EXTRA_BLAZE, data)
    }

fun receiveInvite(ctx: Context, conversationId: String, userId: String? = null, playRing: Boolean, foreground: Boolean) =
    startService<GroupCallService>(ctx, ACTION_KRAKEN_RECEIVE_INVITE, foreground) {
        it.putExtra(EXTRA_CONVERSATION_ID, conversationId)
        it.putExtra(EXTRA_USER_ID, userId)
        it.putExtra(EXTRA_PLAY_RING, playRing)
    }

fun receiveEnd(ctx: Context, conversationId: String, userId: String) =
    startService<GroupCallService>(ctx, ACTION_KRAKEN_RECEIVE_END) {
        it.putExtra(EXTRA_CONVERSATION_ID, conversationId)
        it.putExtra(EXTRA_USER_ID, userId)
    }

fun receiveCancel(ctx: Context, conversationId: String, userId: String) =
    startService<GroupCallService>(ctx, ACTION_KRAKEN_RECEIVE_CANCEL) {
        it.putExtra(EXTRA_CONVERSATION_ID, conversationId)
        it.putExtra(EXTRA_USER_ID, userId)
    }

fun acceptInvite(ctx: Context) = startService<GroupCallService>(ctx, ACTION_KRAKEN_ACCEPT_INVITE, true) {}

fun krakenEnd(ctx: Context) = startService<GroupCallService>(ctx, ACTION_KRAKEN_END) {}

fun krakenCancel(ctx: Context) = startService<GroupCallService>(ctx, ACTION_KRAKEN_CANCEL) {}

fun krakenDecline(ctx: Context) = startService<GroupCallService>(ctx, ACTION_KRAKEN_DECLINE) {}

fun krakenCancelSilently(ctx: Context) = startService<GroupCallService>(ctx, ACTION_KRAKEN_CANCEL_SILENTLY) {}
