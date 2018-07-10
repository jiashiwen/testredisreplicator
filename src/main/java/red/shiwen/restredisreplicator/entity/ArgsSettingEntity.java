package red.shiwen.restredisreplicator.entity;

import com.beust.jcommander.Parameter;

public class ArgsSettingEntity {

    @Parameter(names = "--help", help = true)
    private boolean help = false;
    @Parameter(names = "--source", description = "source redis node default is 'redis://127.0.0.1:6379'")
    private String source = "redis://127.0.0.1:6379";

    @Parameter(names = "--target", description = "target redis node default is 'redis://127.0.0.1:6379'")
    private String target = "redis://127.0.0.1:6379";

    public boolean isHelp() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
}
