function [delta,rsqr] = compTarg(mode,u,osqr,osqR)
%compTarget(mode,u,osqr,osqR) compute target petal data
%   Given two petals in normalized setting, use the
%   reciprocal square roots of their radii to compute 
%   data for the 'target' petal. osqR is the reciprocal
%   of the signed square root of the neighbor (may be 
%   negative or zero), osqr is reciprocal of the square
%   root of the radius of opposite petal, u is uzian 
%   for edge to the neighbor. Mode tells us witch
%   "Situation" we are in, as layed out in the preprint. 
%   'delta' is the displacement of the target's tangency 
%   point from that of the neighbor, rsqr is the reciprocal
%   square root of the neew radius and may be negative or 0.

s3=sqrt(3);

% mode 1, Situation 1, find displacement of c_{n-1}; ignore rad info
if mode==1
    delta=2*s3*u;
    rsqr=1;
    return;
end

% mode 2, Situation 2, find petal c_2; ignore radius data
if mode==2
    delta=2/(s3*u);
    rsqr=s3*u;
    return;
end

% mode 3, Situation 3, generic
if mode==3
    delta=2/(osqR*osqR*s3*u-osqR*osqr);
    rsqr=s3*u*osqR-osqr;
    return;
end

% mode 4, Situation 4, qR is 0 or negative.
if mode==4 
    % When qR=0, target petal is tangent to
    % center at infinity (so it's a half plane with
    % bdry parallel to real axis and through -2*r*i).
    if qR==0
        delta=2*s3*u/(osqr*osqr);
        rsqr=osqr;
        return;
    end
    % When qR<0, previous target was left of the
    % circle for r; formula for delta is same as
    % situation 3, although qR is now negative
    delta=2/(osqR*osqR*s3*u-osqR*osqr);
    rsqr=s3*u*osqR-osqr;
    return;
end

end