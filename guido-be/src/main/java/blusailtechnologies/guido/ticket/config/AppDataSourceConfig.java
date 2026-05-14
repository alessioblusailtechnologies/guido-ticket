package blusailtechnologies.guido.ticket.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class AppDataSourceConfig {

	@Bean(name = "appDataSourceProperties")
	@ConfigurationProperties("guido.app-db")
	DataSourceProperties appDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean(name = "appDataSource")
	DataSource appDataSource(
			@org.springframework.beans.factory.annotation.Qualifier("appDataSourceProperties")
			DataSourceProperties properties) {
		HikariDataSource ds = properties.initializeDataSourceBuilder()
				.type(HikariDataSource.class)
				.build();
		ds.setPoolName("guido-app");
		ds.setAutoCommit(true);
		return ds;
	}

	@Bean(name = "appJdbcTemplate")
	JdbcTemplate appJdbcTemplate(
			@org.springframework.beans.factory.annotation.Qualifier("appDataSource") DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}
}
