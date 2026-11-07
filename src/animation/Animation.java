package animation;

public final class Animation {
    private final AnimationFrame[] frames;
    private final boolean looping;

    public Animation(AnimationFrame[] frames, boolean looping) {
        if (frames.length == 0) {
            throw new IllegalArgumentException("An animation needs at least one frame.");
        }
        this.frames = frames;
        this.looping = looping;
    }

    public AnimationFrame getFrame(int index) {
        return frames[index];
    }

    public int getFrameCount() {
        return frames.length;
    }

    public boolean isLooping() {
        return looping;
    }
}
