package io.coffeebeans.connector.sink;

import io.coffeebeans.connector.sink.config.AzureBlobSinkConfig;
import io.coffeebeans.connector.sink.format.FormatManager;
import io.coffeebeans.connector.sink.format.parquet.ParquetFormatManager;
import io.coffeebeans.connector.sink.metadata.MetadataConsumer;
import io.coffeebeans.connector.sink.partitioner.DefaultPartitioner;
import io.coffeebeans.connector.sink.partitioner.PartitionStrategy;
import io.coffeebeans.connector.sink.partitioner.Partitioner;
import io.coffeebeans.connector.sink.partitioner.field.FieldPartitioner;
import io.coffeebeans.connector.sink.partitioner.time.TimePartitioner;
import io.coffeebeans.connector.sink.storage.BlobStorageManager;
import io.coffeebeans.connector.sink.storage.StorageManager;
import io.coffeebeans.connector.sink.util.StructToMap;
import java.io.IOException;
import java.util.Map;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will select the appropriate Partitioner, Storage manager and Format manager.
 */
public class RecordWriter {
    private static final Logger logger = LoggerFactory.getLogger(RecordWriter.class);

    private final Partitioner partitioner;
    private final FormatManager formatManager;
    private final MetadataConsumer metadataConsumer;

    /**
     * Constructor.
     *
     * @param config - AzureBlobSinkConfig
     */
    public RecordWriter(AzureBlobSinkConfig config) {
        this.partitioner = getPartitioner(config);

        StorageManager storageManager = new BlobStorageManager(config.getConnectionString());
        this.formatManager = new ParquetFormatManager(config, storageManager);
        this.metadataConsumer = new MetadataConsumer(config, this.formatManager.getActiveFileIndexMap());
    }

    /**
     * I will call the FormatManager to buffer the record passed as argument.
     *
     * @param sinkRecord SinkRecord
     * @throws IOException - Thrown if exception occur during writing of record
     * @throws InterruptedException - Thrown if exception occur during updating metadata
     */
    public void bufferRecord(SinkRecord sinkRecord) throws IOException, InterruptedException {
        String fullPath = this.partitioner.generateFullPath(sinkRecord);

        Map<String, Object> valueMap = toValueMap(sinkRecord);
        if (valueMap == null || valueMap.isEmpty()) {
            return;
        }

        try {
            this.formatManager.buffer(fullPath, valueMap);
        } catch (Exception e) {
            logger.error("Failed to write data");
            throw e;
        }
    }

    private Partitioner getPartitioner(AzureBlobSinkConfig config) {
        PartitionStrategy strategy = PartitionStrategy.valueOf(config.getPartitionStrategy());
        logger.info("Partition strategy configured: {}", strategy);

        switch (strategy) {
          case TIME: return new TimePartitioner(config);
          case FIELD: return new FieldPartitioner(config);
          default: return new DefaultPartitioner(config);
        }
    }

    private Map<String, Object> toValueMap(SinkRecord sinkRecord) {
        Map<?, ?> valueMap = null;

        // Only Map or Struct type objects are supported currently
        // Check the type of record and get the Map representation
        if (sinkRecord.value() instanceof Map) {
            valueMap = (Map<?, ?>) sinkRecord.value();

        } else if (sinkRecord.value() instanceof Struct) {
            // Convert Struct to Map
            valueMap = StructToMap.toMap((Struct) sinkRecord.value());
        }

        Map<String, Object> castedValueMap;
        try {
            castedValueMap = (Map<String, Object>) valueMap;
        } catch (Exception e) {
            logger.error("Error while casting value map with exception: {}", e.getMessage());
            throw e;
        }

        return castedValueMap;
    }

    public void startMetadataConsumer() {
        this.metadataConsumer.startPolling();
    }

    public FormatManager getFormatManager() {
        return this.formatManager;
    }
}
