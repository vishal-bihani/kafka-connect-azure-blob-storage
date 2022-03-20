package io.coffeebeans.connector.sink.config.validators;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;

/**
 * This class will validate container name provided by the user as configuration parameter.
 */
public class ContainerNameValidator implements ConfigDef.Validator {

    /**
     * Ensure the provided container name is not null, empty and blank.
     *
     * @param containerNameConfig Container name config
     * @param containerNameValue Container name
     */
    @Override
    public void ensureValid(String containerNameConfig, Object containerNameValue) {
        String containerName = (String) containerNameValue;

        if (containerName == null || containerName.isEmpty() || containerName.isBlank()) {
            // TODO: Check if blob storage support special characters and if not then that check should be done here.
            throw new ConfigException("Invalid container name: ", containerName);
        }
    }
}
