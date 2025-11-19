package to.bitkit.repositories

import com.synonym.bitkitcore.Activity
import com.synonym.bitkitcore.ActivityFilter
import com.synonym.bitkitcore.LightningActivity
import com.synonym.bitkitcore.OnchainActivity
import com.synonym.bitkitcore.PaymentType
import com.synonym.bitkitcore.SortDirection
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Test
import org.lightningdevkit.ldknode.PaymentDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import to.bitkit.data.AppCacheData
import to.bitkit.data.AppDb
import to.bitkit.data.CacheStore
import to.bitkit.data.dto.PendingBoostActivity
import to.bitkit.services.CoreService
import to.bitkit.test.BaseUnitTest
import to.bitkit.utils.AddressChecker
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityRepoTest : BaseUnitTest() {

    private val coreService = mock<CoreService>()
    private val lightningRepo = mock<LightningRepo>()
    private val transferRepo = mock<TransferRepo>()
    private val cacheStore = mock<CacheStore>()
    private val addressChecker = mock<AddressChecker>()
    private val db = mock<AppDb>()
    private val clock = mock<Clock>()

    private lateinit var sut: ActivityRepo

    private val testPaymentDetails = mock<PaymentDetails> {
        on { id } doReturn "payment1"
    }

    private val testActivityV1 = mock<LightningActivity> {
        on { id } doReturn "activity1"
    }

    private val testActivity = mock<Activity.Lightning> {
        on { v1 } doReturn testActivityV1
    }

    private val baseOnchainActivity = OnchainActivity(
        id = "base_activity_id",
        txType = PaymentType.SENT,
        txId = "base_tx_id",
        value = 1000uL,
        fee = 100uL,
        feeRate = 10uL,
        address = "bc1test",
        confirmed = false,
        timestamp = 1234567890uL,
        isBoosted = false,
        boostTxIds = emptyList(),
        isTransfer = false,
        doesExist = true,
        confirmTimestamp = null,
        channelId = null,
        transferTxId = null,
        createdAt = null,
        updatedAt = null
    )

    private fun createOnchainActivity(
        id: String = baseOnchainActivity.id,
        txId: String = baseOnchainActivity.txId,
        value: ULong = baseOnchainActivity.value,
        fee: ULong = baseOnchainActivity.fee,
        feeRate: ULong = baseOnchainActivity.feeRate,
        address: String = baseOnchainActivity.address,
        confirmed: Boolean = baseOnchainActivity.confirmed,
        timestamp: ULong = baseOnchainActivity.timestamp,
        isBoosted: Boolean = baseOnchainActivity.isBoosted,
        boostTxIds: List<String> = baseOnchainActivity.boostTxIds,
        isTransfer: Boolean = baseOnchainActivity.isTransfer,
        doesExist: Boolean = baseOnchainActivity.doesExist,
        confirmTimestamp: ULong? = baseOnchainActivity.confirmTimestamp,
        channelId: String? = baseOnchainActivity.channelId,
        transferTxId: String? = baseOnchainActivity.transferTxId,
        createdAt: ULong? = baseOnchainActivity.createdAt,
        updatedAt: ULong? = baseOnchainActivity.updatedAt,
    ): Activity.Onchain {
        return Activity.Onchain(
            v1 = baseOnchainActivity.copy(
                id = id,
                txId = txId,
                value = value,
                fee = fee,
                feeRate = feeRate,
                address = address,
                confirmed = confirmed,
                timestamp = timestamp,
                isBoosted = isBoosted,
                boostTxIds = boostTxIds,
                isTransfer = isTransfer,
                doesExist = doesExist,
                confirmTimestamp = confirmTimestamp,
                channelId = channelId,
                transferTxId = transferTxId,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        )
    }

    @Before
    fun setUp() {
        whenever(cacheStore.data).thenReturn(flowOf(AppCacheData()))
        whenever(coreService.activity).thenReturn(mock())
        whenever(clock.now()).thenReturn(Clock.System.now())

        sut = ActivityRepo(
            bgDispatcher = testDispatcher,
            coreService = coreService,
            lightningRepo = lightningRepo,
            cacheStore = cacheStore,
            addressChecker = addressChecker,
            db = db,
            transferRepo = transferRepo,
            clock = clock,
        )
    }

    private fun setupSyncActivitiesMocks(
        cacheData: AppCacheData,
        activities: List<Activity> = emptyList()
    ) {
        whenever(cacheStore.data).thenReturn(flowOf(cacheData))
        wheneverBlocking { lightningRepo.getPayments() }.thenReturn(Result.success(emptyList()))
        wheneverBlocking { coreService.activity.syncLdkNodePaymentsToActivities(any(), eq(false)) }.thenReturn(Unit)
        if (activities.isNotEmpty()) {
            wheneverBlocking {
                coreService.activity.get(
                    filter = ActivityFilter.ONCHAIN,
                    txType = PaymentType.SENT,
                    tags = null,
                    search = null,
                    minDate = null,
                    maxDate = null,
                    limit = 10u,
                    sortDirection = null
                )
            }.thenReturn(activities)
        }
    }

    @Test
    fun `syncActivities success flow`() = test {
        val payments = listOf(testPaymentDetails)
        wheneverBlocking { lightningRepo.getPayments() }.thenReturn(Result.success(payments))
        wheneverBlocking { coreService.activity.getActivity(any()) }.thenReturn(null)
        wheneverBlocking { coreService.activity.syncLdkNodePaymentsToActivities(payments) }.thenReturn(Unit)
        wheneverBlocking { transferRepo.syncTransferStates() }.thenReturn(Result.success(Unit))
        wheneverBlocking { coreService.activity.allPossibleTags() }.thenReturn(emptyList())

        val result = sut.syncActivities()

        assertTrue(result.isSuccess)
        verify(lightningRepo).getPayments()
        verify(coreService.activity).syncLdkNodePaymentsToActivities(payments)
        assertFalse(sut.isSyncingLdkNodePayments.value)
    }

    @Test
    fun `syncActivities handles lightningRepo failure`() = test {
        val exception = Exception("Lightning repo failed")
        wheneverBlocking { lightningRepo.getPayments() }.thenReturn(Result.failure(exception))

        val result = sut.syncActivities()

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        assertFalse(sut.isSyncingLdkNodePayments.value)
    }

    @Test
    fun `findActivityByPaymentId returns failure when activity not found after sync`() = test {
        val paymentId = "payment123"

        wheneverBlocking {
            coreService.activity.get(
                filter = any(),
                txType = any(),
                tags = any(),
                search = any(),
                minDate = any(),
                maxDate = any(),
                limit = any(),
                sortDirection = any()
            )
        }.thenReturn(emptyList())

        wheneverBlocking { lightningRepo.sync() }.thenReturn(Result.success(Unit))
        wheneverBlocking { lightningRepo.getPayments() }.thenReturn(Result.success(emptyList()))

        val result = sut.findActivityByPaymentId(
            paymentHashOrTxId = paymentId,
            type = ActivityFilter.LIGHTNING,
            txType = PaymentType.RECEIVED
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `getActivities returns activities successfully`() = test {
        val activities = listOf(testActivity)
        wheneverBlocking {
            coreService.activity.get(
                filter = ActivityFilter.ALL,
                txType = PaymentType.RECEIVED,
                tags = listOf("tag1"),
                search = "search",
                minDate = 1000u,
                maxDate = 2000u,
                limit = 50u,
                sortDirection = SortDirection.DESC
            )
        }.thenReturn(activities)

        val result = sut.getActivities(
            filter = ActivityFilter.ALL,
            txType = PaymentType.RECEIVED,
            tags = listOf("tag1"),
            search = "search",
            minDate = 1000u,
            maxDate = 2000u,
            limit = 50u,
            sortDirection = SortDirection.DESC
        )

        assertTrue(result.isSuccess)
        assertEquals(activities, result.getOrThrow())
    }

    @Test
    fun `getActivity returns activity when found`() = test {
        val activityId = "activity123"
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(testActivity)

        val result = sut.getActivity(activityId)

        assertTrue(result.isSuccess)
        assertEquals(testActivity, result.getOrThrow())
    }

    @Test
    fun `getActivity returns null when not found`() = test {
        val activityId = "activity123"
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(null)

        val result = sut.getActivity(activityId)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `updateActivity updates successfully when not deleted`() = test {
        val activityId = "activity123"
        val cacheData = AppCacheData(deletedActivities = emptyList())
        whenever(cacheStore.data).thenReturn(flowOf(cacheData))
        wheneverBlocking { coreService.activity.update(activityId, testActivity) }.thenReturn(Unit)

        val result = sut.updateActivity(activityId, testActivity)

        assertTrue(result.isSuccess)
        verify(coreService.activity).update(activityId, testActivity)
    }

    @Test
    fun `updateActivity fails when activity is deleted and forceUpdate is false`() = test {
        val activityId = "activity123"
        val cacheData = AppCacheData(deletedActivities = listOf(activityId))
        whenever(cacheStore.data).thenReturn(flowOf(cacheData))

        val result = sut.updateActivity(activityId, testActivity, forceUpdate = false)

        assertTrue(result.isFailure)
        verify(coreService.activity, never()).update(any(), any())
    }

    @Test
    fun `updateActivity succeeds when activity is deleted but forceUpdate is true`() = test {
        val activityId = "activity123"
        val cacheData = AppCacheData(deletedActivities = listOf(activityId))
        whenever(cacheStore.data).thenReturn(flowOf(cacheData))
        wheneverBlocking { coreService.activity.update(activityId, testActivity) }.thenReturn(Unit)

        val result = sut.updateActivity(activityId, testActivity, forceUpdate = true)

        assertTrue(result.isSuccess)
        verify(coreService.activity).update(activityId, testActivity)
    }

    @Test
    fun `replaceActivity updates and marks old activity as removed from mempool`() = test {
        val activityId = "activity123"
        val activityToDeleteId = "activity456"
        val tagsMock = listOf("tag1", "tag2")
        val cacheData = AppCacheData(deletedActivities = emptyList())
        whenever(cacheStore.data).thenReturn(flowOf(cacheData))

        // Mock the activity to be marked as removed (must be Onchain)
        val onchainActivityToDelete = createOnchainActivity(id = activityToDeleteId, txId = "tx123")

        // Mock update for the new activity
        wheneverBlocking { coreService.activity.update(activityId, testActivity) }.thenReturn(Unit)
        // Mock getActivity to return the new activity (for addTagsToActivity check)
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(testActivity)
        // Mock getActivity to return the onchain activity to be marked as removed
        wheneverBlocking { coreService.activity.getActivity(activityToDeleteId) }.thenReturn(onchainActivityToDelete)
        // Mock update for the old activity (with doesExist=false)
        wheneverBlocking { coreService.activity.update(eq(activityToDeleteId), any()) }.thenReturn(Unit)
        // Mock tags retrieval from the old activity
        wheneverBlocking { coreService.activity.tags(activityToDeleteId) }.thenReturn(tagsMock)
        // Mock tags retrieval from the new activity (should be empty so all tags are considered new)
        wheneverBlocking { coreService.activity.tags(activityId) }.thenReturn(emptyList())
        // Mock appendTags to add tags to the new activity
        wheneverBlocking { coreService.activity.appendTags(activityId, tagsMock) }.thenReturn(Result.success(Unit))

        val result = sut.replaceActivity(activityId, activityToDeleteId, testActivity)

        assertTrue(result.isSuccess)
        // Verify the new activity is updated
        verify(coreService.activity).update(activityId, testActivity)
        // Verify the old activity is retrieved
        verify(coreService.activity).getActivity(activityToDeleteId)
        // Verify tags are retrieved from the old activity
        verify(coreService.activity).tags(activityToDeleteId)
        // Verify tags are added to the new activity
        verify(coreService.activity).appendTags(activityId, tagsMock)
        // Verify the old activity is updated (marked as removed from mempool with doesExist=false)
        verify(coreService.activity).update(
            eq(activityToDeleteId),
            argThat { activity ->
                activity is Activity.Onchain && !activity.v1.doesExist
            }
        )
        // Verify delete is NOT called
        verify(coreService.activity, never()).delete(any())
        // Verify addActivityToDeletedList is NOT called
        verify(cacheStore, never()).addActivityToDeletedList(any())
    }

    @Test
    fun `deleteActivity deletes successfully`() = test {
        val activityId = "activity123"
        wheneverBlocking { coreService.activity.delete(activityId) }.thenReturn(true)
        wheneverBlocking { cacheStore.addActivityToDeletedList(activityId) }.thenReturn(Unit)

        val result = sut.deleteActivity(activityId)

        assertTrue(result.isSuccess)
        verify(coreService.activity).delete(activityId)
        verify(cacheStore).addActivityToDeletedList(activityId)
    }

    @Test
    fun `deleteActivity fails when service returns false`() = test {
        val activityId = "activity123"
        wheneverBlocking { coreService.activity.delete(activityId) }.thenReturn(false)

        val result = sut.deleteActivity(activityId)

        assertTrue(result.isFailure)
        verify(cacheStore, never()).addActivityToDeletedList(any())
    }

    @Test
    fun `insertActivity inserts successfully when not deleted`() = test {
        val cacheData = AppCacheData(deletedActivities = emptyList())
        whenever(cacheStore.data).thenReturn(flowOf(cacheData))
        wheneverBlocking { coreService.activity.insert(testActivity) }.thenReturn(Unit)

        val result = sut.insertActivity(testActivity)

        assertTrue(result.isSuccess)
        verify(coreService.activity).insert(testActivity)
    }

    @Test
    fun `insertActivity fails when activity is deleted`() = test {
        val cacheData = AppCacheData(deletedActivities = listOf("activity1"))
        whenever(cacheStore.data).thenReturn(flowOf(cacheData))

        val result = sut.insertActivity(testActivity)

        assertTrue(result.isFailure)
        verify(coreService.activity, never()).insert(any())
    }

    @Test
    fun `addTagsToActivity adds new tags successfully`() = test {
        val activityId = "activity123"
        val existingTags = listOf("tag1", "tag2")
        val newTags = listOf("tag2", "tag3", "tag4", "") // tag2 exists, empty string should be filtered
        val expectedNewTags = listOf("tag3", "tag4")

        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(testActivity)
        wheneverBlocking { coreService.activity.tags(activityId) }.thenReturn(existingTags)
        wheneverBlocking {
            coreService.activity.appendTags(
                activityId,
                expectedNewTags
            )
        }.thenReturn(Result.success(Unit))

        val result = sut.addTagsToActivity(activityId, newTags)

        assertTrue(result.isSuccess)
        verify(coreService.activity).appendTags(activityId, expectedNewTags)
    }

    @Test
    fun `addTagsToActivity fails when activity not found`() = test {
        val activityId = "activity123"
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(null)

        val result = sut.addTagsToActivity(activityId, listOf("tag1"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `addTagsToActivity does nothing when no new tags`() = test {
        val activityId = "activity123"
        val existingTags = listOf("tag1", "tag2")
        val duplicateTags = listOf("tag1", "tag2", "")

        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(testActivity)
        wheneverBlocking { coreService.activity.tags(activityId) }.thenReturn(existingTags)

        val result = sut.addTagsToActivity(activityId, duplicateTags)

        assertTrue(result.isSuccess)
        verify(coreService.activity, never()).appendTags(any(), any())
    }

    @Test
    fun `attachTagsToActivity should fail with empty tags`() = test {
        val result = sut.addTagsToTransaction(
            paymentHashOrTxId = "txId",
            type = ActivityFilter.ALL,
            txType = PaymentType.SENT,
            tags = emptyList()
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `attachTagsToActivity should fail with empty paymentHashOrTxId`() = test {
        val result = sut.addTagsToTransaction(
            paymentHashOrTxId = "",
            type = ActivityFilter.ALL,
            txType = PaymentType.SENT,
            tags = listOf("tag1")
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `removeTagsFromActivity removes tags successfully`() = test {
        val activityId = "activity123"
        val tagsToRemove = listOf("tag1", "tag2")

        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(testActivity)
        wheneverBlocking { coreService.activity.dropTags(activityId, tagsToRemove) }.thenReturn(Unit)

        val result = sut.removeTagsFromActivity(activityId, tagsToRemove)

        assertTrue(result.isSuccess)
        verify(coreService.activity).dropTags(activityId, tagsToRemove)
    }

    @Test
    fun `removeTagsFromActivity fails when activity not found`() = test {
        val activityId = "activity123"
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(null)

        val result = sut.removeTagsFromActivity(activityId, listOf("tag1"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `getActivityTags returns tags successfully`() = test {
        val activityId = "activity123"
        val tags = listOf("tag1", "tag2", "tag3")
        wheneverBlocking { coreService.activity.tags(activityId) }.thenReturn(tags)

        val result = sut.getActivityTags(activityId)

        assertTrue(result.isSuccess)
        assertEquals(tags, result.getOrThrow())
    }

    @Test
    fun `getAllAvailableTags returns all tags successfully`() = test {
        val allTags = listOf("tag1", "tag2", "tag3", "tag4")
        wheneverBlocking { coreService.activity.allPossibleTags() }.thenReturn(allTags)

        val result = sut.getAllAvailableTags()

        assertTrue(result.isSuccess)
        assertEquals(allTags, result.getOrThrow())
    }

    @Test
    fun `removeAllActivities removes all activities successfully`() = test {
        wheneverBlocking { coreService.activity.removeAll() }.thenReturn(Unit)

        val result = sut.removeAllActivities()

        assertTrue(result.isSuccess)
        verify(coreService.activity).removeAll()
    }

    @Test
    fun `generateTestData generates with validated count`() = test {
        wheneverBlocking { coreService.activity.generateRandomTestData(any()) }.thenReturn(Unit)

        val result = sut.generateTestData(50)

        assertTrue(result.isSuccess)
        verify(coreService.activity).generateRandomTestData(50)
    }

    @Test
    fun `generateTestData coerces count to valid range`() = test {
        wheneverBlocking { coreService.activity.generateRandomTestData(any()) }.thenReturn(Unit)

        val result = sut.generateTestData(1500) // Over limit

        assertTrue(result.isSuccess)
        verify(coreService.activity).generateRandomTestData(1000) // Should be coerced to max
    }

    @Test
    fun `addActivityToPendingBoost adds to cache`() = test {
        val pendingBoost = PendingBoostActivity(
            txId = "tx123",
            updatedAt = 2000u,
            activityToDelete = null
        )
        wheneverBlocking { cacheStore.addActivityToPendingBoost(pendingBoost) }.thenReturn(Unit)

        sut.addActivityToPendingBoost(pendingBoost)

        verify(cacheStore).addActivityToPendingBoost(pendingBoost)
    }

    @Test
    fun `markActivityAsRemovedFromMempool successfully marks onchain activity as removed`() = test {
        val activityId = "activity456"
        val onchainActivity = createOnchainActivity(
            id = activityId,
            txId = "tx123",
            doesExist = true // Initially exists
        )

        val cacheData = AppCacheData(activitiesPendingDelete = listOf(activityId))
        setupSyncActivitiesMocks(cacheData)
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(onchainActivity)
        wheneverBlocking { coreService.activity.update(eq(activityId), any()) }.thenReturn(Unit)
        wheneverBlocking { cacheStore.removeActivityFromPendingDelete(activityId) }.thenReturn(Unit)

        val result = sut.syncActivities()

        assertTrue(result.isSuccess)
        // Verify the activity was marked as removed (doesExist = false)
        verify(coreService.activity).update(
            eq(activityId),
            argThat { activity ->
                activity is Activity.Onchain &&
                    !activity.v1.doesExist &&
                    activity.v1.id == activityId &&
                    activity.v1.txId == "tx123"
            }
        )
        // Verify it was removed from pending delete after successful marking
        verify(cacheStore).removeActivityFromPendingDelete(activityId)
    }

    @Test
    fun `boostPendingActivities adds parentTxId to boostTxIds when parentTxId is provided`() = test {
        val txId = "tx123"
        val parentTxId = "parentTx456"
        val activityId = "activity123"
        val updatedAt = 2000uL

        val existingActivity = createOnchainActivity(
            id = activityId,
            txId = txId,
            updatedAt = 1000uL
        )

        val pendingBoost = PendingBoostActivity(
            txId = txId,
            updatedAt = updatedAt,
            activityToDelete = null,
            parentTxId = parentTxId
        )

        val cacheData = AppCacheData(pendingBoostActivities = listOf(pendingBoost))
        setupSyncActivitiesMocks(cacheData, listOf(existingActivity))
        wheneverBlocking { coreService.activity.update(eq(activityId), any()) }.thenReturn(Unit)
        wheneverBlocking { cacheStore.removeActivityFromPendingBoost(pendingBoost) }.thenReturn(Unit)

        val result = sut.syncActivities()

        assertTrue(result.isSuccess)
        // Verify update was called with parentTxId added to empty boostTxIds
        verify(coreService.activity).update(
            eq(activityId),
            argThat { activity ->
                activity is Activity.Onchain && activity.v1.boostTxIds == listOf(parentTxId)
            }
        )
        verify(cacheStore).removeActivityFromPendingBoost(pendingBoost)
    }

    @Test
    fun `boostPendingActivities preserves existing boostTxIds when adding parentTxId`() = test {
        val txId = "tx123"
        val parentTxId = "parentTx456"
        val existingBoostTxId = "existingBoost123"
        val activityId = "activity123"
        val updatedAt = 2000uL

        val existingActivity = createOnchainActivity(
            id = activityId,
            txId = txId,
            boostTxIds = listOf(existingBoostTxId),
            updatedAt = 1000uL
        )

        val pendingBoost = PendingBoostActivity(
            txId = txId,
            updatedAt = updatedAt,
            activityToDelete = null,
            parentTxId = parentTxId
        )

        val cacheData = AppCacheData(pendingBoostActivities = listOf(pendingBoost))
        setupSyncActivitiesMocks(cacheData, listOf(existingActivity))
        wheneverBlocking { coreService.activity.update(eq(activityId), any()) }.thenReturn(Unit)
        wheneverBlocking { cacheStore.removeActivityFromPendingBoost(pendingBoost) }.thenReturn(Unit)

        val result = sut.syncActivities()

        assertTrue(result.isSuccess)
        // Verify update was called with both existing and new parentTxId in boostTxIds
        verify(coreService.activity).update(
            eq(activityId),
            argThat { activity ->
                activity is Activity.Onchain &&
                    activity.v1.boostTxIds.contains(existingBoostTxId) &&
                    activity.v1.boostTxIds.contains(parentTxId)
            }
        )
    }

    @Test
    fun `boostPendingActivities does not add parentTxId when parentTxId is null`() = test {
        val txId = "tx123"
        val existingBoostTxId = "existingBoost123"
        val activityId = "activity123"
        val updatedAt = 2000uL

        val existingActivity = createOnchainActivity(
            id = activityId,
            txId = txId,
            boostTxIds = listOf(existingBoostTxId),
            updatedAt = 1000uL
        )

        val pendingBoost = PendingBoostActivity(
            txId = txId,
            updatedAt = updatedAt,
            activityToDelete = null,
            parentTxId = null
        )

        val cacheData = AppCacheData(pendingBoostActivities = listOf(pendingBoost))
        setupSyncActivitiesMocks(cacheData, listOf(existingActivity))
        wheneverBlocking { coreService.activity.update(eq(activityId), any()) }.thenReturn(Unit)
        wheneverBlocking { cacheStore.removeActivityFromPendingBoost(pendingBoost) }.thenReturn(Unit)

        val result = sut.syncActivities()

        assertTrue(result.isSuccess)
        // Verify update was called with only existing boostTxIds (no new parentTxId added)
        verify(coreService.activity).update(
            eq(activityId),
            argThat { activity ->
                activity is Activity.Onchain &&
                    activity.v1.boostTxIds == listOf(existingBoostTxId)
            }
        )
    }

    @Test
    fun `boostPendingActivities calls replaceActivity when activityToDelete is provided`() = test {
        val txId = "tx123"
        val parentTxId = "parentTx456"
        val activityId = "activity123"
        val activityToDeleteId = "activity456"
        val updatedAt = 2000uL

        val existingActivity = createOnchainActivity(
            id = activityId,
            txId = txId,
            updatedAt = 1000uL
        )

        val onchainActivityToDelete = createOnchainActivity(
            id = activityToDeleteId,
            txId = "oldTx123",
            value = 500uL,
            fee = 50uL,
            feeRate = 5uL,
            address = "bc1old",
            timestamp = 1234560000uL
        )

        val pendingBoost = PendingBoostActivity(
            txId = txId,
            updatedAt = updatedAt,
            activityToDelete = activityToDeleteId,
            parentTxId = parentTxId
        )

        val cacheData = AppCacheData(pendingBoostActivities = listOf(pendingBoost))
        setupSyncActivitiesMocks(cacheData, listOf(existingActivity))
        wheneverBlocking { coreService.activity.update(eq(activityId), any()) }.thenReturn(Unit)
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(existingActivity)
        wheneverBlocking { coreService.activity.getActivity(activityToDeleteId) }.thenReturn(onchainActivityToDelete)
        wheneverBlocking { coreService.activity.update(eq(activityToDeleteId), any()) }.thenReturn(Unit)
        wheneverBlocking { coreService.activity.tags(activityToDeleteId) }.thenReturn(emptyList())
        wheneverBlocking { coreService.activity.tags(activityId) }.thenReturn(emptyList())
        wheneverBlocking { cacheStore.removeActivityFromPendingBoost(pendingBoost) }.thenReturn(Unit)

        val result = sut.syncActivities()

        assertTrue(result.isSuccess)
        // Verify replaceActivity was called (indirectly by checking both activities were updated)
        verify(coreService.activity).update(eq(activityId), any())
        // Verify the old activity was marked as removed (doesExist = false)
        verify(coreService.activity).update(
            eq(activityToDeleteId),
            argThat { activity ->
                activity is Activity.Onchain && !activity.v1.doesExist
            }
        )
        verify(cacheStore).removeActivityFromPendingBoost(pendingBoost)
    }

    @Test
    fun `boostPendingActivities skips when activity updatedAt is newer than pendingBoost updatedAt`() = test {
        val txId = "tx123"
        val activityId = "activity123"
        val updatedAt = 2000uL

        val existingActivity = createOnchainActivity(
            id = activityId,
            txId = txId,
            updatedAt = 3000uL // Newer than pendingBoost.updatedAt
        )

        val pendingBoost = PendingBoostActivity(
            txId = txId,
            updatedAt = updatedAt,
            activityToDelete = null,
            parentTxId = null
        )

        val cacheData = AppCacheData(pendingBoostActivities = listOf(pendingBoost))
        setupSyncActivitiesMocks(cacheData, listOf(existingActivity))
        wheneverBlocking { cacheStore.removeActivityFromPendingBoost(pendingBoost) }.thenReturn(Unit)

        val result = sut.syncActivities()

        assertTrue(result.isSuccess)
        // Verify update was NOT called (activity is newer)
        verify(coreService.activity, never()).update(eq(activityId), any())
        // Verify pending boost was removed (skipped)
        verify(cacheStore).removeActivityFromPendingBoost(pendingBoost)
    }

    @Test
    fun `markActivityAsRemovedFromMempool fails when activity not found`() = test {
        val activityId = "activity456"
        val cacheData = AppCacheData(activitiesPendingDelete = listOf(activityId))
        setupSyncActivitiesMocks(cacheData)
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(null)

        val result = sut.syncActivities()

        assertTrue(result.isSuccess)
        // Verify update was NOT called (activity not found)
        verify(coreService.activity, never()).update(eq(activityId), any())
        // Verify it was NOT removed from pending delete (operation failed, will retry next sync)
        verify(cacheStore, never()).removeActivityFromPendingDelete(activityId)
    }

    @Test
    fun `markActivityAsRemovedFromMempool fails when activity is not Onchain`() = test {
        val activityId = "activity456"
        val lightningActivity = testActivity
        val cacheData = AppCacheData(activitiesPendingDelete = listOf(activityId))
        setupSyncActivitiesMocks(cacheData)
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(lightningActivity)

        val result = sut.syncActivities()

        assertTrue(result.isSuccess)
        // Verify update was NOT called (Lightning activities can't be marked as removed)
        verify(coreService.activity, never()).update(eq(activityId), any())
        // Verify it was NOT removed from pending delete (operation failed, will retry next sync)
        verify(cacheStore, never()).removeActivityFromPendingDelete(activityId)
    }

    @Test
    fun `replaceActivity caches to pending delete when markActivityAsRemovedFromMempool fails`() = test {
        val activityId = "activity123"
        val activityToDeleteId = "activity456"
        val cacheData = AppCacheData(deletedActivities = emptyList())
        whenever(cacheStore.data).thenReturn(flowOf(cacheData))

        // Activity to delete doesn't exist (will cause markActivityAsRemovedFromMempool to fail)
        wheneverBlocking { coreService.activity.update(activityId, testActivity) }.thenReturn(Unit)
        wheneverBlocking { coreService.activity.getActivity(activityId) }.thenReturn(testActivity)
        wheneverBlocking { coreService.activity.getActivity(activityToDeleteId) }.thenReturn(null)
        wheneverBlocking { coreService.activity.tags(activityToDeleteId) }.thenReturn(emptyList())
        wheneverBlocking { coreService.activity.tags(activityId) }.thenReturn(emptyList())
        wheneverBlocking { cacheStore.addActivityToPendingDelete(activityToDeleteId) }.thenReturn(Unit)

        val result = sut.replaceActivity(activityId, activityToDeleteId, testActivity)

        assertTrue(result.isSuccess)
        // Verify it was added to pending delete when marking failed
        verify(cacheStore).addActivityToPendingDelete(activityToDeleteId)
    }

}
