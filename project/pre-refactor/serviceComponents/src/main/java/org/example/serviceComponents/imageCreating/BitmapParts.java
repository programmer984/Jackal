package org.example.serviceComponents.imageCreating;

import java.util.ArrayList;
import java.util.List;

public class BitmapParts {



    private ImagePartsConfiguration configuration;
    private List<ImagePart> parts = new ArrayList<>();

    public BitmapParts(ImagePartsConfiguration configuration) {
        this.configuration = configuration;
    }


    public ImagePartsConfiguration getConfiguration() {
        return configuration;
    }

    public List<ImagePart> getParts() {
        return parts;
    }
    public void setParts(List<ImagePart> parts) {
        this.parts = parts;
    }


}
