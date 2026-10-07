package utils;

public class D2Coordinates {
    int X;
    int Y;

    // Constructor that accepts a string like "150,20"
    public D2Coordinates(String coordinateStr) {
        String[] parts = coordinateStr.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid coordinate format. Expected format: \"X,Y\"");
        }

        try {
            this.X = Integer.parseInt(parts[0].trim());
            this.Y = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Coordinates must be integers.", e);
        }
    }

    // Optional: toString() for easy printing
    @Override
    public String toString() {
        return "D2Coordinates{" + "X=" + X + ", Y=" + Y + '}';
    }
}
