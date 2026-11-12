package org.bnel.dicom;

/** Short, shared hover explanations for application actions. */
final class ActionHelp {
    static String forButton(String text){
        String help=switch(text){
            case "Import folder" -> "Add one or more DICOM folders, including their subfolders.";
            case "Add files" -> "Add individual DICOM files to the current session.";
            case "Remove selected" -> "Remove the selected files or branches from this session. Original files stay in place.";
            case "Clean selected" -> "Create cleaned copies of the selected files using your current metadata settings.";
            case "Review images" -> "Open images and reports for optional visual inspection, marking and exclusion.";
            case "Export cleaned" -> "Save the cleaned files to a new folder or ZIP using the selected export options.";
            case "Cancel" -> "Close this dialog without applying changes.";
            case "Clear session" -> "Unload all imported files and discard temporary cleaned copies. Originals stay in place.";
            case "Compare files" -> "Find metadata values that differ or are missing across the selected files.";
            case "Scan labels…" -> "Edit series, protocol and sequence labels used in cleaned copies.";
            case "Identifier rules…" -> "Choose identifier replacements and persistent custom tag-removal rules.";
            case "Previous image" -> "Show the previous slice or frame in tree order, skipping excluded files.";
            case "Next image" -> "Show the next slice or frame in tree order, skipping excluded files.";
            case "Play", "Pause" -> "Play or pause the slices at the selected frame rate. Shortcut: Space.";
            case "Auto / Fit" -> "Restore this image's default window settings and fit it to the viewport.";
            case "Apply blackout to all frames" -> "Apply the drawn rectangles to every frame of the current cleaned file. Originals stay in place.";
            case "Clear rectangles" -> "Remove the drawn rectangles before applying a blackout.";
            case "Exclude marked from export" -> "Exclude all marked files. If none are marked, exclude the displayed file. Apply exclusions when the viewer closes.";
            case "Undo exclusion" -> "Include the displayed excluded file again, unless it was moved to Recycle Bin.";
            case "Move originals to Recycle Bin…" -> "Move marked originals, or the displayed original if none are marked, to Recycle Bin after confirmation.";
            case "Select all unmarked" -> "Select every unmarked, unexcluded file across all source folders and series.";
            case "Mark all selected" -> "Flag all files in the selected branches or rows for later exclusion or removal.";
            case "Unmark selected" -> "Clear marks on selected files. Files that are already unmarked are left unchanged.";
            case "Approve selected" -> "Mark selected, unmarked and unexcluded files as reviewed. Approval is optional for export.";
            case "Remove approval of selected" -> "Clear review approvals for selected files or branches. Unapproved files are left unchanged.";
            case "Remove all approvals" -> "Clear every review approval in this viewer, regardless of the current selection.";
            case "Collapse" -> "Collapse the file lists while keeping source folders, subjects, studies and series visible.";
            case "Expand" -> "Expand every source and series to show individual DICOM files.";
            case "Full value" -> "Open the complete value of the selected metadata tag.";
            case "Copy displayed value" -> "Copy the displayed metadata value to the clipboard.";
            case "Inspect first 64 KB" -> "Preview the first 64 KB of the selected binary metadata element.";
            case "Save full element" -> "Save the selected metadata element's raw contents to a file.";
            case "Add removal rule" -> "Add this tag to the removal list for this and future cleaning jobs.";
            case "Remove selected rule" -> "Remove selected custom rules so those tags follow the normal cleaning policy.";
            case "Save rules" -> "Apply these identifier rules and remember them for future sessions.";
            case "Save labels" -> "Apply label overrides to this session. Clean the files again to use them.";
            case "Copy details" -> "Copy these results, including reference paths, to the clipboard.";
            case "Close" -> "Close this window and return to the main workspace.";
            default -> null;
        };
        return help==null?null:tip(help);
    }
    static String tip(String text){return text.length()<85?text:"<html><body style='width:280px'>"+text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</body></html>";}
}
