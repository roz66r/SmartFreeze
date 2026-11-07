package com.golu.smartfreeze;
import android.view.View;
import android.view.ViewParent;
final class ViewParentTools {
    private ViewParentTools() {}
    static void disallowParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }
}
