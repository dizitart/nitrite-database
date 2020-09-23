package org.dizitart.no2.transaction;

import org.dizitart.no2.Nitrite;
import org.dizitart.no2.NitriteConfig;
import org.dizitart.no2.collection.*;
import org.dizitart.no2.collection.events.CollectionEventInfo;
import org.dizitart.no2.collection.events.CollectionEventListener;
import org.dizitart.no2.collection.meta.Attributes;
import org.dizitart.no2.collection.operation.CollectionOperations;
import org.dizitart.no2.common.WriteResult;
import org.dizitart.no2.common.event.EventBus;
import org.dizitart.no2.common.event.NitriteEventBus;
import org.dizitart.no2.exceptions.*;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.index.IndexEntry;
import org.dizitart.no2.index.IndexOptions;
import org.dizitart.no2.index.IndexType;
import org.dizitart.no2.store.NitriteMap;
import org.dizitart.no2.store.NitriteStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.dizitart.no2.collection.UpdateOptions.updateOptions;
import static org.dizitart.no2.common.util.DocumentUtils.createUniqueFilter;
import static org.dizitart.no2.common.util.ValidationUtils.containsNull;
import static org.dizitart.no2.common.util.ValidationUtils.notNull;
import static org.dizitart.no2.index.IndexOptions.indexOptions;

/**
 * @author Anindya Chatterjee
 */
class DefaultTransactionalCollection implements NitriteCollection {
    private final NitriteCollection primary;
    private final TransactionContext transactionContext;
    private final Nitrite nitrite;

    private String collectionName;
    private NitriteMap<NitriteId, Document> nitriteMap;
    private NitriteStore<?> nitriteStore;
    private Lock writeLock;
    private Lock readLock;
    private CollectionOperations collectionOperations;
    private EventBus<CollectionEventInfo<?>, CollectionEventListener> eventBus;
    private volatile boolean isDropped;
    private volatile boolean isClosed;

    public DefaultTransactionalCollection(NitriteCollection primary,
                                          TransactionContext transactionContext,
                                          Nitrite nitrite) {
        this.primary = primary;
        this.transactionContext = transactionContext;
        this.nitrite = nitrite;

        initialize();
    }

    @Override
    public WriteResult insert(Document[] documents) {
        checkOpened();
        notNull(documents, "a null document cannot be inserted");
        containsNull(documents, "a null document cannot be inserted");

        for (Document document : documents) {
            // generate ids
            document.getId();
        }

        WriteResult result;
        try {
            writeLock.lock();
            result = collectionOperations.insert(documents);
        } finally {
            writeLock.unlock();
        }

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.Insert);
        journalEntry.setCommit(() -> primary.insert(documents));
        journalEntry.setRollback(() -> {
            for (Document document : documents) {
                primary.remove(document);
            }
        });
        transactionContext.getJournal().add(journalEntry);

        return result;
    }

    @Override
    public WriteResult update(Filter filter, Document update, UpdateOptions updateOptions) {
        checkOpened();
        notNull(update, "a null document cannot be used for update");
        notNull(updateOptions, "updateOptions cannot be null");

        WriteResult result;
        try {
            writeLock.lock();
            result = collectionOperations.update(filter, update, updateOptions);
        } finally {
            writeLock.unlock();
        }

        List<Document> documentList = new ArrayList<>();

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.Update);
        journalEntry.setCommit(() -> {
            DocumentCursor cursor = primary.find(filter);

            if (!cursor.isEmpty()) {
                if (updateOptions.isJustOnce()) {
                    documentList.add(cursor.firstOrNull());
                } else {
                    documentList.addAll(cursor.toList());
                }
            }
            primary.update(filter, update, updateOptions);
        });
        journalEntry.setRollback(() -> {
            for (Document document : documentList) {
                primary.remove(document);
                primary.insert(document);
            }
        });
        transactionContext.getJournal().add(journalEntry);

        return result;
    }

    @Override
    public WriteResult update(Document document, boolean insertIfAbsent) {
        checkOpened();

        notNull(document, "a null document cannot be used for update");

        if (insertIfAbsent) {
            return update(createUniqueFilter(document), document, updateOptions(true));
        } else {
            if (document.hasId()) {
                return update(createUniqueFilter(document), document, updateOptions(false));
            } else {
                throw new NotIdentifiableException("update operation failed as no id value found for the document");
            }
        }
    }

    @Override
    public WriteResult remove(Document document) {
        checkOpened();
        notNull(document, "a null document cannot be removed");

        WriteResult result;
        if (document.hasId()) {
            try {
                writeLock.lock();
                result = collectionOperations.remove(document);
            } finally {
                writeLock.unlock();
            }
        } else {
            throw new NotIdentifiableException("remove operation failed as no id value found for the document");
        }

        AtomicReference<Document> toRemove = new AtomicReference<>();

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.Remove);
        journalEntry.setCommit(() -> {
            toRemove.set(primary.getById(document.getId()));
            primary.remove(document);
        });
        journalEntry.setRollback(() -> {
            if (toRemove.get() != null) {
                primary.insert(toRemove.get());
            }
        });
        transactionContext.getJournal().add(journalEntry);

        return result;
    }

    @Override
    public WriteResult remove(Filter filter, boolean justOne) {
        checkOpened();
        if ((filter == null || filter == Filter.ALL) && justOne) {
            throw new InvalidOperationException("remove all cannot be combined with just once");
        }

        WriteResult result;
        try {
            writeLock.lock();
            result = collectionOperations.remove(filter, justOne);
        } finally {
            writeLock.unlock();
        }

        List<Document> documentList = new ArrayList<>();

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.Remove);
        journalEntry.setCommit(() -> {
            DocumentCursor cursor = primary.find(filter);

            if (!cursor.isEmpty()) {
                if (justOne) {
                    documentList.add(cursor.firstOrNull());
                } else {
                    documentList.addAll(cursor.toList());
                }
            }
            primary.remove(filter, justOne);
        });
        journalEntry.setRollback(() -> {
            for (Document document : documentList) {
                primary.insert(document);
            }
        });
        transactionContext.getJournal().add(journalEntry);

        return result;
    }

    @Override
    public DocumentCursor find() {
        checkOpened();
        try {
            readLock.lock();
            return collectionOperations.find();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public DocumentCursor find(Filter filter) {
        checkOpened();

        try {
            readLock.lock();
            return collectionOperations.find(filter);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Document getById(NitriteId nitriteId) {
        checkOpened();
        notNull(nitriteId, "nitriteId cannot be null");

        try {
            readLock.lock();
            return collectionOperations.getById(nitriteId);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public String getName() {
        return collectionName;
    }

    @Override
    public void createIndex(String field, IndexOptions indexOptions) {
        checkOpened();
        notNull(field, "field cannot be null");

        // by default async is false while creating index
        try {
            writeLock.lock();
            if (indexOptions == null) {
                collectionOperations.createIndex(field, IndexType.Unique, false);
            } else {
                collectionOperations.createIndex(field, indexOptions.getIndexType(),
                    indexOptions.isAsync());
            }
        } finally {
            writeLock.unlock();
        }

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.CreateIndex);
        journalEntry.setCommit(() -> primary.createIndex(field, indexOptions));
        journalEntry.setRollback(() -> primary.dropIndex(field));
        transactionContext.getJournal().add(journalEntry);
    }

    @Override
    public void rebuildIndex(String field, boolean isAsync) {
        checkOpened();
        notNull(field, "field cannot be null");

        IndexEntry indexEntry;
        try {
            readLock.lock();
            indexEntry = collectionOperations.findIndex(field);
        } finally {
            readLock.unlock();
        }

        if (indexEntry != null) {
            validateRebuildIndex(indexEntry);

            try {
                writeLock.lock();
                collectionOperations.rebuildIndex(indexEntry, isAsync);
            } finally {
                writeLock.unlock();
            }
        } else {
            throw new IndexingException(field + " is not indexed");
        }

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.RebuildIndex);
        journalEntry.setCommit(() -> primary.rebuildIndex(field, isAsync));
        journalEntry.setRollback(() -> primary.rebuildIndex(field, isAsync));
        transactionContext.getJournal().add(journalEntry);
    }

    @Override
    public Collection<IndexEntry> listIndices() {
        checkOpened();

        try {
            readLock.lock();
            return collectionOperations.listIndexes();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean hasIndex(String field) {
        checkOpened();
        notNull(field, "field cannot be null");

        try {
            readLock.lock();
            return collectionOperations.hasIndex(field);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean isIndexing(String field) {
        checkOpened();
        notNull(field, "field cannot be null");

        try {
            readLock.lock();
            return collectionOperations.isIndexing(field);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void dropIndex(String field) {
        checkOpened();
        notNull(field, "field cannot be null");

        try {
            writeLock.lock();
            collectionOperations.dropIndex(field);
        } finally {
            writeLock.unlock();
        }

        final AtomicReference<IndexEntry> indexEntry = new AtomicReference<>();

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.DropIndex);
        journalEntry.setCommit(() -> {
            for (IndexEntry entry : primary.listIndices()) {
                if (entry.getField().equals(field)) {
                    indexEntry.set(entry);
                    break;
                }
            }
            primary.dropIndex(field);
        });
        journalEntry.setRollback(() -> {
            if (indexEntry.get() != null) {
                primary.createIndex(indexEntry.get().getField(),
                    indexOptions(indexEntry.get().getIndexType()));
            }
        });
        transactionContext.getJournal().add(journalEntry);
    }

    @Override
    public void dropAllIndices() {
        checkOpened();

        try {
            writeLock.lock();
            collectionOperations.dropAllIndices();
        } finally {
            writeLock.unlock();
        }

        List<IndexEntry> indexEntries = new ArrayList<>();

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.DropAllIndices);
        journalEntry.setCommit(() -> {
            indexEntries.addAll(primary.listIndices());
            primary.dropAllIndices();
        });
        journalEntry.setRollback(() -> {
            for (IndexEntry indexEntry : indexEntries) {
                primary.createIndex(indexEntry.getField(), indexOptions(indexEntry.getIndexType()));
            }
        });
        transactionContext.getJournal().add(journalEntry);
    }

    @Override
    public void clear() {
        checkOpened();
        try {
            writeLock.lock();
            nitriteMap.clear();
        } finally {
            writeLock.unlock();
        }

        List<Document> documentList = new ArrayList<>();

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.Clear);
        journalEntry.setCommit(() -> {
            documentList.addAll(primary.find().toList());
            primary.clear();
        });
        journalEntry.setRollback(() -> {
            for (Document document : documentList) {
                primary.insert(document);
            }
        });
        transactionContext.getJournal().add(journalEntry);
    }

    @Override
    public void drop() {
        checkOpened();

        try {
            writeLock.lock();
            collectionOperations.dropCollection();
        } finally {
            writeLock.unlock();
        }
        isDropped = true;

        List<Document> documentList = new ArrayList<>();
        List<IndexEntry> indexEntries = new ArrayList<>();

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.DropCollection);
        journalEntry.setCommit(() -> {
            documentList.addAll(primary.find().toList());
            indexEntries.addAll(primary.listIndices());
            primary.drop();
        });
        journalEntry.setRollback(() -> {
            NitriteCollection collection = nitrite.getCollection(collectionName);

            for (IndexEntry indexEntry : indexEntries) {
                collection.createIndex(indexEntry.getField(), indexOptions(indexEntry.getIndexType()));
            }

            for (Document document : documentList) {
                collection.insert(document);
            }
        });
        transactionContext.getJournal().add(journalEntry);
    }

    @Override
    public boolean isDropped() {
        return isDropped;
    }

    @Override
    public boolean isOpen() {
        if (nitriteStore == null || nitriteStore.isClosed() || isDropped) {
            close();
            return false;
        } else return true;
    }

    @Override
    public void close() {
        if (collectionOperations != null) {
            collectionOperations.close();
        }
        closeEventBus();
        isClosed = true;
    }

    @Override
    public long size() {
        return find().size();
    }

    @Override
    public NitriteStore<?> getStore() {
        return nitriteStore;
    }

    @Override
    public void subscribe(CollectionEventListener listener) {
        checkOpened();
        notNull(listener, "listener cannot be null");

        eventBus.register(listener);
    }

    @Override
    public void unsubscribe(CollectionEventListener listener) {
        checkOpened();
        notNull(listener, "listener cannot be null");

        if (eventBus != null) {
            eventBus.deregister(listener);
        }
    }

    @Override
    public Attributes getAttributes() {
        checkOpened();

        try {
            readLock.lock();
            return collectionOperations.getAttributes();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void setAttributes(Attributes attributes) {
        checkOpened();
        notNull(attributes, "attributes cannot be null");

        try {
            writeLock.lock();
            collectionOperations.setAttributes(attributes);
        } finally {
            writeLock.unlock();
        }

        AtomicReference<Attributes> original = new AtomicReference<>();

        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setChangeType(ChangeType.SetAttribute);
        journalEntry.setCommit(() -> {
            original.set(primary.getAttributes());
            primary.setAttributes(attributes);
        });
        journalEntry.setRollback(() -> {
            if (original.get() != null) {
                primary.setAttributes(original.get());
            }
        });
        transactionContext.getJournal().add(journalEntry);
    }

    private void initialize() {
        this.collectionName = transactionContext.getCollectionName();
        this.nitriteMap = transactionContext.getNitriteMap();
        NitriteConfig nitriteConfig = transactionContext.getConfig();
        this.nitriteStore = nitriteConfig.getNitriteStore();
        this.isDropped = false;

        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();

        this.eventBus = new CollectionEventBus();
        this.collectionOperations = new CollectionOperations(collectionName, nitriteMap, nitriteConfig, eventBus);
    }

    private static class CollectionEventBus extends NitriteEventBus<CollectionEventInfo<?>, CollectionEventListener> {

        public void post(CollectionEventInfo<?> collectionEventInfo) {
            for (final CollectionEventListener listener : getListeners()) {
                getEventExecutor().submit(() -> listener.onEvent(collectionEventInfo));
            }
        }
    }

    private void checkOpened() {
        if (isClosed) {
            throw new TransactionException("collection is closed");
        }

        if (!primary.isOpen()) {
            throw new NitriteIOException("store is closed");
        }

        if (isDropped()) {
            throw new NitriteIOException("collection has been dropped");
        }

        if (!transactionContext.getActive().get()) {
            throw new TransactionException("transaction is closed");
        }
    }

    private void closeEventBus() {
        if (eventBus != null) {
            eventBus.close();
        }
        eventBus = null;
    }

    private void validateRebuildIndex(IndexEntry indexEntry) {
        notNull(indexEntry, "index cannot be null");

        if (isIndexing(indexEntry.getField())) {
            throw new IndexingException("indexing on value " + indexEntry.getField() + " is currently running");
        }
    }
}
