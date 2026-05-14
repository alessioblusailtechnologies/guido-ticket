package blusailtechnologies.guido.ticket.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class OracleDataSourceConfig {

	@Bean(name = "oracleDataSourceProperties")
	@Primary
	@ConfigurationProperties("spring.datasource")
	DataSourceProperties oracleDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean(name = "oracleDataSource")
	@Primary
	@ConfigurationProperties("spring.datasource.hikari")
	DataSource oracleDataSource(
			@org.springframework.beans.factory.annotation.Qualifier("oracleDataSourceProperties")
			DataSourceProperties properties) {
		HikariDataSource ds = properties.initializeDataSourceBuilder()
				.type(HikariDataSource.class)
				.build();
		// I valori di spring.datasource.hikari.* (read-only, pool-name, maximum-pool-size)
		// vengono iniettati da @ConfigurationProperties sopra.
		return ds;
	}

	@Bean(name = "oracleJdbcTemplate")
	@Primary
	JdbcTemplate oracleJdbcTemplate(
			@org.springframework.beans.factory.annotation.Qualifier("oracleDataSource") DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}
}
