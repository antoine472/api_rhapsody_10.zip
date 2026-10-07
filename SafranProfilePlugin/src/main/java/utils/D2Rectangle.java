package utils;

public class D2Rectangle {
    int width;
    int height;
    D2Coordinates position;

    public D2Rectangle(String widthStr, String heightStr, String positionStr) {
        try {
            this.width = Integer.parseInt(widthStr.trim());
            this.height = Integer.parseInt(heightStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Width and height must be valid integers.", e);
        }

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Width and height must be non-negative.");
        }

        this.position = new D2Coordinates(positionStr);
    }

    @Override
    public String toString() {
        return "Rectangle{" +
                "width=" + width +
                ", height=" + height +
                ", position=" + position +
                '}';
    }

	public String getTargetPosition() {
		int xTargetPosition = position.X + width/8;
		int yTargetPosition = position.Y + height;
		
		return xTargetPosition+","+yTargetPosition;
	}

	public String getSourcePosition() {
		int xTargetPosition = position.X;
		int yTargetPosition = position.Y + height/2;
		
		return xTargetPosition+","+yTargetPosition;
		
	}
}
