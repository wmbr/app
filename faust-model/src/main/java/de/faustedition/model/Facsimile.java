package de.faustedition.model;

import java.io.File;

public class Facsimile {

	private Transcription transcription;
	private File imageFile;

	public Facsimile(Transcription transcription, File imageFile) {
		this.transcription = transcription;
		this.imageFile = imageFile;
	}

	public Transcription getTranscription() {
		return transcription;
	}

	public File getImageFile() {
		return imageFile;
	}
}
