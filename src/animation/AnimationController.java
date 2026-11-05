package animation;

public final class AnimationController {
    private AnimationState state = AnimationState.IDLE;
    private Direction direction = Direction.DOWN;
    private Animation animation;
    private int frameIndex;
    private long frameElapsedNanos;
    private boolean finished;

    public void setAnimation(AnimationState state, Direction direction, Animation animation) {
        if (this.state == state && this.direction == direction && this.animation == animation) {
            return;
        }
        this.state = state;
        this.direction = direction;
        this.animation = animation;
        frameIndex = 0;
        frameElapsedNanos = 0;
        finished = false;
    }

    public void update(long deltaNanos) {
        if (animation == null || finished) {
            return;
        }
        frameElapsedNanos += Math.max(0, deltaNanos);
        while (frameElapsedNanos >= animation.getFrame(frameIndex).getDurationNanos()) {
            frameElapsedNanos -= animation.getFrame(frameIndex).getDurationNanos();
            if (frameIndex == animation.getFrameCount() - 1) {
                if (animation.isLooping()) {
                    frameIndex = 0;
                } else {
                    finished = true;
                }
                break;
            }
            frameIndex++;
        }
    }

    public AnimationState getState() {
        return state;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public boolean isFinished() {
        return finished;
    }

    public AnimationFrame getCurrentFrame() {
        return animation.getFrame(frameIndex);
    }
}
