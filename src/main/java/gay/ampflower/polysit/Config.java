package gay.ampflower.polysit;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * @author Ampflower
 * @since 0.9.10.1
 **/
public final class Config {
	private static final Logger logger = LogUtils.getLogger();

	public static float reach = 5;

	public static void init() {
		final Path path = FabricLoader.getInstance().getConfigDir().resolve("polysit.properties");
		final Properties properties = new Properties();

		try {
			if (Files.exists(path)) {
				read(path, properties);
			} else {
				write(path, properties);
			}
		} catch (IOException e) {
			logger.warn("Unable to init Polysit config, resetting to default.", e);

			return;
		}

		reach = Float.parseFloat(properties.getProperty("reach"));
	}

	private static void read(final Path path, final Properties properties) throws IOException {
		properties.clear();

		try (final Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
	}

	private static void write(final Path path, final Properties properties) throws IOException {
		properties.setProperty("reach", String.valueOf(reach));

		Files.createDirectories(path.getParent());

		try (final Writer writer = Files.newBufferedWriter(
			path,
			StandardCharsets.UTF_8,
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING
		)) {
			properties.store(writer, "Polysit Configuration");
		}
	}
}
