use actix_cors::Cors;
use actix_web::{web, App, HttpServer};
use sqlx::sqlite::SqlitePoolOptions;
use sqlx::SqlitePool;

mod db;
mod models;
mod routes;

pub struct AppState {
    pub db: SqlitePool,
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    env_logger::init();
    dotenv::dotenv().ok();

    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "sqlite:pwd_server.db?mode=rwc".to_string());

    let pool = SqlitePoolOptions::new()
        .max_connections(5)
        .connect(&database_url)
        .await
        .expect("Failed to connect to database");

    db::migrate(&pool).await;

    let data = web::Data::new(AppState { db: pool });

    log::info!("Starting PWD server on 0.0.0.0:8080");

    HttpServer::new(move || {
        let cors = Cors::default()
            .allow_any_origin()
            .allow_any_method()
            .allow_any_header();

        App::new()
            .wrap(cors)
            .app_data(data.clone())
            .configure(routes::configure)
    })
    .bind("0.0.0.0:8080")?
    .run()
    .await
}
