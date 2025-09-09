package com.example.thesisapp; // Or your actual package for API models

import com.google.gson.annotations.SerializedName;
import java.util.List; // Import List

public class FishDetailsResponse {

    @SerializedName("status") // Match the server's "status" key
    private String status;    // Store the status string, e.g., "success"

    @SerializedName("fish_class")
    private List<String> fishClassList; // Expects a List of Strings

    @SerializedName("fish_weight")
    private List<Double> fishWeightList; // Expects a List of Doubles (or Floats)

    // --- Getters ---

    public String getStatus() {
        return status;
    }

    // Helper method to get the first fish class if the list is not empty
    public String getFishClass() {
        if (fishClassList != null && !fishClassList.isEmpty()) {
            return fishClassList.get(0); // Get the first item from the list
        }
        return null; // Or return a default value like "N/A"
    }

    // Helper method to get the first fish weight if the list is not empty
    public Double getFishWeightValue() { // Changed name to avoid conflict if you keep original getWeight()
        if (fishWeightList != null && !fishWeightList.isEmpty()) {
            return fishWeightList.get(0); // Get the first item from the list
        }
        return null; // Or return 0.0 or handle as needed
    }

    // You can add a method to check if the status is "success"
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }


    // --- Setters (optional) ---
    public void setStatus(String status) {
        this.status = status;
    }

    public void setFishClassList(List<String> fishClassList) {
        this.fishClassList = fishClassList;
    }

    public void setFishWeightList(List<Double> fishWeightList) {
        this.fishWeightList = fishWeightList;
    }

    @Override
    public String toString() {
        return "FishDetailsResponse{" +
                "status='" + status + '\'' +
                ", fishClassList=" + fishClassList +
                ", fishWeightList=" + fishWeightList +
                '}';
    }

    // Your original getWeight method might need adjustment or a new one
    // if you want to directly access the parsed double value.
    public float getWeight() {
        Double weightValue = getFishWeightValue();
        if (weightValue != null) {
            return weightValue.floatValue();
        }
        // Handle case where weight is not available, e.g., return 0 or throw exception
        return 0.0f; // Or some other default / error indicator
    }
}
