package linker;

public class AliasConflictException extends RuntimeException {

    public AliasConflictException(String alias) {
        super("Alias is already in use");
    }
}
