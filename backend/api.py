print("--- VERSÃO 38 DO CÓDIGO A SER EXECUTADA ---")

from fastapi import FastAPI, HTTPException
from google.cloud import texttospeech, storage
import uuid
from typing import List, Dict, Any, Optional
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
import google.auth
from pydantic import BaseModel, Field

CLOUD_STORAGE_BUCKET = "agente-pessoal-ia"

class EmailItem(BaseModel):
    from_sender: Optional[str] = Field(alias="from")
    subject: Optional[str]
    snippet: Optional[str]

class EmailRequest(BaseModel):
    emails: List[EmailItem]

llm = None
tts_client = None
storage_client = None

app = FastAPI()

def initialize_clients():
    global llm, tts_client, storage_client

    if llm is not None:
        return

    print("INFO: Initializing Google Cloud Clients for the first time.")
    try:
        print("INFO: Initializing LLM using GOOGLE_API_KEY from environment.")
        llm = ChatGoogleGenerativeAI(model="gemini-1.5-flash")

        credentials, project_id = google.auth.default()
        tts_client = texttospeech.TextToSpeechClient(credentials=credentials)
        storage_client = storage.Client(credentials=credentials)

        print("INFO: Clients initialized successfully.")
    except Exception as e:
        print(f"CRITICAL ERROR initializing clients: {e}")
        raise HTTPException(status_code=503, detail=f"Não foi possível inicializar os serviços de IA: {e}")

def generate_summary_from_emails(emails: List[Dict[str, Any]]) -> str:
    """Uses the LLM to generates a spoken summary from emails list."""
    try:
        if not llm:
            raise HTTPException(status_code=500, detail="The language model is not available.")

        if not emails:
            return "Você não tem novos e-mails não lidos."

        try:
            prompt = ChatPromptTemplate.from_messages([
                ("system", "Você é uma assistente pessoal chamada IA. A sua tarefa é criar um resumo verbal, conciso e natural"
                           " dos e-mails não lidos do seu utilizador. Comece sempre com uma saudação. Seja direta e priorize o"
                           " que parece mais importante."),
                ("user", "Aqui estão os meus e-mails não lidos:\n\n{email_list}")
            ])

            output_parser = StrOutputParser()

            chain = prompt | llm | output_parser

            formatted_emails = "\n---\n".join([f"De: {e['from']}\nAssunto: {e['subject']}\nPrévia: {e['snippet']}" for e in emails])

            summary = chain.invoke({"email_list": formatted_emails})
            return summary
        except Exception as e:
            print(f"DETAILED ERROR in generate_summary_from_emails: {e}")
            raise HTTPException(status_code=500, detail="Falha ao gerar o resumo com o LLM.")
    except Exception as e:
        print(f"CRITICAL ERROR in generate_summary_from_emails: {e}")
        raise

def text_to_speech_and_upload(text_to_speak: str) -> str:
    """
    Converts the text to audio and returns the public URL.
    """
    try:
        if not tts_client or not storage_client:
            raise ConnectionError("TTS Client or Storage not initialized.")

        try:
            synthesis_input = texttospeech.SynthesisInput(text=text_to_speak)
            voice = texttospeech.VoiceSelectionParams(
                language_code="pt-BR",
                name="pt-BR-Wavenet-C"
            )
            audio_config = texttospeech.AudioConfig(
                audio_encoding=texttospeech.AudioEncoding.MP3
            )
            response = tts_client.synthesize_speech(
                input=synthesis_input, voice=voice, audio_config=audio_config
            )
            bucket = storage_client.bucket(CLOUD_STORAGE_BUCKET)
            blob_name = f"summary-{uuid.uuid4()}.mp3"
            blob = bucket.blob(blob_name)
            blob.upload_from_string(response.audio_content, content_type="audio/mpeg")
            return blob.public_url

        except Exception as e:
            print(f"An error occurred while generating or uploading the audio: {e}")
            raise HTTPException(status_code=500, detail="Error generating or uploading the audio.")
    except Exception as e:
        print(f"CRITICAL ERROR in text_to_speech_and_upload: {e}")
        raise

@app.on_event("startup")
async def startup_event():
    initialize_clients()

@app.get("/")
def read_root():
    """
    API main endpoint.
    """
    return {"status": "online", "message": "Welcome to IA Personal Agent Backend. Go to /docs to see the endpoints."}

@app.post("/generate-summary-audio")
def generate_summary_endpoint(request_body: EmailRequest):
    """Receive a emails list, generates a smart summary, converts to audio, and returns the URL audio file."""
    try:
        emails = [email.dict(by_alias=True) for email in request_body.emails]
        summary_text = generate_summary_from_emails(emails)
        audio_url = text_to_speech_and_upload(summary_text)

        return {"audio_url": audio_url, "summary_text_for_debug": summary_text}
    except HTTPException as e:
        raise e
    except Exception as e:
        print(f"FATAL ERROR in endpoint /generate-summary-audio: {type(e).__name__} - {e}")
        raise HTTPException(status_code=500, detail=f"Ocorreu um erro interno inesperado: {type(e).__name__}")