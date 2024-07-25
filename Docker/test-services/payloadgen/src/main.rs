use rand::prelude::*;
use rocket::tokio::io::ReadBuf;
use rocket::{
    self,
    data::{Data, ToByteUnit},
    get,
    http::Status,
    post, put,
    response::stream::ReaderStream,
    routes, tokio,
};
use std::io;
use std::pin::Pin;
use std::task::{Context, Poll};

struct RandomResponse {
    size: usize,
    rng: SmallRng,
}

impl tokio::io::AsyncRead for RandomResponse {
    fn poll_read(
        mut self: Pin<&mut Self>,
        _cx: &mut Context,
        buf: &mut ReadBuf,
    ) -> Poll<tokio::io::Result<()>> {
        let remaining = buf.remaining();
        if self.size > 0 {
            if self.size > remaining {
                self.rng.fill(buf.initialize_unfilled());
                self.size -= remaining;
                buf.advance(remaining);
            } else {
                let s = self.size;
                self.rng.fill(buf.initialize_unfilled_to(s));
                self.size = 0;
                buf.advance(s);
            }
        }
        Poll::Ready(Ok(()))
    }
}

#[get("/data/<size>")]
async fn generate(size: usize) -> io::Result<ReaderStream![RandomResponse]> {
    Ok(ReaderStream::one(RandomResponse {
        size,
        rng: SmallRng::from_rng(rand::thread_rng()).unwrap(),
    }))
}

#[post("/data", data = "<data>")]
async fn upload(data: Data<'_>) -> (Status, io::Result<String>) {
    let result = data.open(1.gibibytes()).stream_to(tokio::io::sink()).await;

    match result {
        Ok(n) if n.complete => (Status::Created, Ok(n.to_string())),
        Ok(n) => (Status::BadRequest, Ok(n.to_string())),
        Err(e) => (Status::InternalServerError, Err(e)),
    }
}

#[put("/data", data = "<data>")]
async fn put(data: Data<'_>) -> (Status, io::Result<String>) {
    upload(data).await
}

#[rocket::main]
async fn main() -> Result<(), rocket::Error> {
    rocket::build()
        .mount("/", routes![generate, upload, put])
        .launch()
        .await
}
